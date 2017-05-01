package gov.va.escreening.vista;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import gov.va.escreening.constants.AssessmentConstants;
import gov.va.escreening.constants.TemplateConstants;
import gov.va.escreening.constants.TemplateConstants.TemplateType;
import gov.va.escreening.constants.TemplateConstants.ViewType;
import gov.va.escreening.delegate.SaveToVistaContext;
import gov.va.escreening.delegate.VistaDelegate;
import gov.va.escreening.domain.AssessmentStatusEnum;
import gov.va.escreening.domain.MentalHealthAssessment;
import gov.va.escreening.entity.*;
import gov.va.escreening.entity.HealthFactor;
import gov.va.escreening.repository.AssessmentAppointmentRepository;
import gov.va.escreening.repository.VeteranAssessmentAuditLogRepository;
import gov.va.escreening.repository.VistaRepository;
import gov.va.escreening.security.EscreenUser;
import gov.va.escreening.service.AssessmentEngineService;
import gov.va.escreening.service.VeteranAssessmentService;
import gov.va.escreening.templateprocessor.TemplateProcessorService;
import gov.va.escreening.util.SurveyResponsesHelper;
import gov.va.escreening.vista.dto.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

@Service("vistaDelegate")
public class VistaDelegateImpl implements VistaDelegate, MessageSourceAware {

    private final Logger logger = LoggerFactory.getLogger(VistaDelegateImpl.class);
    @Resource(name = "surveyResponsesHelper")
    SurveyResponsesHelper surveyResponsesHelper;
    @Resource(name = "rpcConnectionProvider")
    RpcConnectionProvider rpcConnectionProvider;
    @Resource(name = "assessmentEngineService")
    AssessmentEngineService assessmentEngineService;
    private MessageSource messageSource;
    private VeteranAssessmentService veteranAssessmentService;
    @Value("${quick.order.ien}")
    private long quickOrderIen;
    @Value("${ref.tbi.service.name}")
    private String refTbiServiceName;
    @Autowired
    private VeteranAssessmentAuditLogRepository veteranAssessmentAuditLogRepository;

    @Autowired
    private TemplateProcessorService templateProcessorService;

    @Autowired
    private VistaRepository vistaRepo;

    @Autowired
    private AssessmentAppointmentRepository assessmentApptRepo;

    @Autowired
    public void setVeteranAssessmentService(
            VeteranAssessmentService veteranAssessmentService) {
        this.veteranAssessmentService = veteranAssessmentService;
    }

    @Transactional(readOnly = false)
    @Override
    public void saveVeteranAssessmentToVista(SaveToVistaContext ctxt) throws VistaLinkClientException {

        Integer vaId = ctxt.getVeteranAssessmentId();

        VeteranAssessment veteranAssessment = checkVeteranAssessment(ctxt);
        if(veteranAssessment.getAssessmentStatus().getAssessmentStatusId() != AssessmentStatusEnum.COMPLETE.getAssessmentStatusId())
        {
            logger.warn("Can not save assessment if it is not in COMPLETE state, current state=" + veteranAssessment.getAssessmentStatus().getAssessmentStatusId());
            return;
        }
        logger.trace("sva2vista:SaveToVistaContext after checkVeteranAssessment:{}", ctxt);
        logger.trace("sva2vista:VeteranAssessment returned by checkVeteranAssessment:{}", veteranAssessment);
        if (ctxt.opFailed(SaveToVistaContext.PendingOperation.veteran)) {
            return;
        }

        final VistaLinkClientStrategy vistaLinkClientStrategy = rpcConnectionProvider.createVistaLinkClientStrategy(ctxt.getEscUserId(), "", "OR CPRS GUI CHART");
        logger.trace("sva2vista:vaid:{}--VistaLinkClientStrategy:{}", vaId, vistaLinkClientStrategy);

        final VistaLinkClient vistaLinkClient = vistaLinkClientStrategy.getClient();
        logger.trace("sva2vista:vaid:{}--VistaLinkClient:{}", vaId, vistaLinkClient);
        Long patientIEN = Long.parseLong(veteranAssessment.getVeteran().getVeteranIen());
        logger.trace("sva2vista:vaid:{}--patientIEN:{}", vaId, patientIEN);

        {
            // Get Mental Health Assessments
            saveMentalHealthAssessments(patientIEN, veteranAssessment, vistaLinkClient, ctxt);
            logger.trace("sva2vista:SaveToVistaContext after saveMentalHealthAssessments:{}", ctxt);
        }

        // Generate CPRS Note based on the responses to the survey
        // questions.
        Long locationIEN = Long.parseLong(veteranAssessment.getClinic().getVistaIen());
        logger.trace("sva2vista:vaid:{}--locationIEN:{}", vaId, locationIEN);

        Boolean inpatientStatus = vistaLinkClient.findPatientDemographics(patientIEN).getInpatientStatus();
        logger.trace("sva2vista:vaid:{}--inpatientStatus:{}", vaId, inpatientStatus);

        String visitStrFromVista = findVisitStrFromVista(ctxt.getEscUserId(), patientIEN.toString(), veteranAssessment);
        logger.trace("sva2vista:vaid:{}--visitStr:{}", vaId, visitStrFromVista);
        // vista will not return any visitStr if assessments does not have any appointments
        boolean hasAppointments = visitStrFromVista != null;

        String visitString = hasAppointments ? visitStrFromVista : createVisitStrLocallyFromAssessment(vistaLinkClient, locationIEN, inpatientStatus, vaId, veteranAssessment);
        logger.trace("sva2vista:vaid:{}--visitString:{}", vaId, visitString);

        {
            VistaProgressNote progressNote = saveProgressNote(patientIEN, locationIEN, visitString, veteranAssessment, vistaLinkClient, ctxt);
            logger.trace("sva2vista:vaid:{}--VistaProgressNote:{}", vaId, progressNote);

            if (progressNote != null && progressNote.getIEN() != null) {
                saveMentalHealthFactors(hasAppointments, locationIEN, visitString, inpatientStatus, progressNote.getIEN(), veteranAssessment, vistaLinkClient, ctxt);
                logger.trace("sva2vista:vaid:{}--saveMentalHealthFactors:{}", vaId, ctxt);
                if (ctxt.opFailed(SaveToVistaContext.PendingOperation.hf)) {
                    return;
                }
            }
        }

        {
            // save TBI Consult request
            saveTbiConsultRequest(veteranAssessment, vistaLinkClient, ctxt);
            logger.trace("sva2vista:vaid:{}--saveTbiConsultRequest:{}", vaId, ctxt);
        }

        {
            savePainScale(veteranAssessment, veteranAssessment.getUpdateAsFileman(), vistaLinkClient, ctxt);
            logger.trace("sva2vista:vaid:{}--savePainScale:{}", vaId, ctxt);
        }

        {
            // save this activity in audit log
            VeteranAssessmentAuditLog auditLogEntry = VeteranAssessmentAuditLogHelper.createAuditLogEntry(veteranAssessment, AssessmentConstants.ASSESSMENT_EVENT_VISTA_SAVE, veteranAssessment.getAssessmentStatus().getAssessmentStatusId(), AssessmentConstants.PERSON_TYPE_USER);
            if(ctxt.getEscUserId() != null)
            {
                auditLogEntry.setPersonFirstName(ctxt.getEscUserId().getFirstName());
                auditLogEntry.setPersonLastName(ctxt.getEscUserId().getLastName());
                auditLogEntry.setPersonId(ctxt.getEscUserId().getUserId());
            }
            logger.trace("sva2vista:vaid:{}--VeteranAssessmentAuditLog:{}", vaId, ctxt);
            veteranAssessmentAuditLogRepository.update(auditLogEntry);
            logger.trace("sva2vista:vaid:{}--audit entry log saved successfully", vaId);
        }
        {
            assessmentEngineService.transitionAssessmentStatusTo(veteranAssessment.getVeteranAssessmentId(), AssessmentStatusEnum.FINALIZED);
            logger.trace("sva2vista:vaid:{}--transitionAssessmentStatusTo FINALIZED", vaId);
        }
    }

    private String createVisitStrLocallyFromAssessment(VistaLinkClient vistaLinkClient, Long locationIEN, Boolean inpatientStatus, Integer vaId, VeteranAssessment veteranAssessment) {
        VistaServiceCategoryEnum encounterServiceCategory = vistaLinkClient.findServiceCategory(VistaServiceCategoryEnum.A, locationIEN, inpatientStatus);
        logger.trace("sva2vista:vaid:{}--encounterServiceCategory:{}", vaId, encounterServiceCategory);

        String assessmentDateAsFileman = veteranAssessment.getUpdateAsFileman();

        return String.format("%s;%s;%s", locationIEN, assessmentDateAsFileman, ((encounterServiceCategory != null) ? encounterServiceCategory.name() : VistaServiceCategoryEnum.A.name()));
    }

    private void savePainScale(VeteranAssessment veteranAssessment, String visitDate, VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) {
        vistaLinkClient.savePainScale(veteranAssessment, visitDate, ctxt);
        ctxt.requestDone(SaveToVistaContext.PendingOperation.pain_scale);
    }

    private VeteranAssessment checkVeteranAssessment(SaveToVistaContext ctxt) {
        logger.trace("sva2vista:vaid:{}--checkVeteranAssessment:{}", ctxt);
        VeteranAssessment veteranAssessment = null;
        if (!ctxt.getEscUserId().getCprsVerified()) {
            ctxt.addUserError(SaveToVistaContext.PendingOperation.veteran, msg(SaveToVistaContext.MsgKey.usr_err_vet__verification));
        } else {
            // 1. Get Veteran's assessment.
            veteranAssessment = veteranAssessmentService.findByVeteranAssessmentId(ctxt.getVeteranAssessmentId());
            if (veteranAssessment == null) {
                ctxt.addUserError(SaveToVistaContext.PendingOperation.veteran, msg(SaveToVistaContext.MsgKey.usr_err_vet__not_found));
            } else if (StringUtils.isEmpty(veteranAssessment.getVeteran().getVeteranIen())) {
                // 2. Make sure Veteran has been mapped to a VistA record. Else, this
                // will not work.
                ctxt.addUserError(SaveToVistaContext.PendingOperation.veteran, msg(SaveToVistaContext.MsgKey.usr_err_vet__failed_mapping));
            }
        }
        ctxt.requestDone(SaveToVistaContext.PendingOperation.veteran);
        return veteranAssessment;
    }

    private String findVisitStrFromVista(EscreenUser user, String vetIen, VeteranAssessment assessment) {
        AssessmentAppointment assessAppt = assessmentApptRepo.findByAssessmentId(assessment.getVeteranAssessmentId());
        if (assessAppt == null) return null;

        long date = assessAppt.getAppointmentDate().getTime();
        Calendar c = Calendar.getInstance();
        c.setTime(assessAppt.getAppointmentDate());
        c.add(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59); //Set the end date to midnight next day.
        List<VistaVeteranAppointment> apptList = vistaRepo.getVeteranAppointments(user.getVistaDivision(),
                user.getVistaVpid(), user.getVistaDuz(), assessAppt.getAppointmentDate(), c.getTime(), vetIen);

        VistaVeteranAppointment picked = null;
        if (apptList != null) {
            for (VistaVeteranAppointment appt : apptList) {
                if (appt.getClinicName() != null && appt.getClinicName().equals(assessment.getClinic().getName())) {
                    if (picked == null) {
                        picked = appt;
                    } else if (Math.abs(picked.getAppointmentDate().getTime() - date)
                            > Math.abs(appt.getAppointmentDate().getTime() - date)) {
                        picked = appt;
                    }
                }
            }
        } else {
            logger.warn("No Appointment list found for start date {}, end date {}, veteran IEN {}, using user with ID {}", 
                    new Object[] {assessAppt.getAppointmentDate(), c.getTime(), vetIen, user.getUserId()});
        }

        if (picked != null) {
            String visitString = picked.getVisitStr();
            String[] part = visitString.split(";");
            if (part.length == 3) {
                return String.format("%s;%s;%s", part[2], part[1], part[0]);
            }
        }
        return null;
    }

    private void saveTbiConsultRequest(VeteranAssessment veteranAssessment,
                                       VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) {
        try {
            //if (true) {throw new IllegalStateException("BTBIS EXCEPTION for JSP to handle the callResults logic");}
            Survey btbisSurvey = isTBIConsultSelected(veteranAssessment);
            if (btbisSurvey != null) {
                checkNotNull(veteranAssessment, "Veteran Assessment cannot be null");
                checkNotNull(quickOrderIen, "Quick Order IEN cannot be null");
                checkNotNull(refTbiServiceName, "Tbi Service Name cannot be null");
                checkNotNull(surveyResponsesHelper, "Survey Responses Helper cannot be null");
                String consultReason = templateProcessorService.renderSurveyTemplate(btbisSurvey.getSurveyId(),
                        TemplateType.TBI_CONSULT_REASON, veteranAssessment, ViewType.TEXT);
                Map<String, Object> vistaResponse = vistaLinkClient.saveTBIConsultOrders(veteranAssessment, quickOrderIen, refTbiServiceName, consultReason,
                        surveyResponsesHelper.prepareSurveyResponsesMap(btbisSurvey.getName(), veteranAssessment.getSurveyMeasureResponseList(), true));
                logger.trace("sva2vista:ctxt:{}--TBI Consult Response {}", ctxt, vistaResponse);
                ctxt.addSuccess(SaveToVistaContext.PendingOperation.tbi, msg(SaveToVistaContext.MsgKey.usr_pass_tbi__saved_success));
            }
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.tbi, Throwables.getRootCause(e).getMessage());
        }
        ctxt.requestDone(SaveToVistaContext.PendingOperation.tbi);
    }

    private Survey isTBIConsultSelected(VeteranAssessment veteranAssessment) {
        for (SurveyMeasureResponse smr : veteranAssessment.getSurveyMeasureResponseList()) {
            if ("BTBIS".equals(smr.getSurvey().getName())) {
                MeasureAnswer ma = smr.getMeasureAnswer();
                if ("1".equals(ma.getCalculationValue()) && smr.getBooleanValue()) {
                    return smr.getSurvey();
                }
            }
        }
        return null;
    }


    private String msg(SaveToVistaContext.MsgKey msgKey, Object... args) {
        return messageSource.getMessage(msgKey.name(), args, null);
    }

    private void saveMentalHealthFactors(boolean hasAppointments, Long locationIEN, String visitString,
                                         Boolean inpatientStatus, Long progressNoteIEN,
                                         VeteranAssessment veteranAssessment, VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) throws VistaLinkClientException {

        HealthFactorProvider healthFactorProvider = createHealthFactorProvider(veteranAssessment);

        HealthFactorHeader healthFactorHeader = createHealthFactorHeader(inpatientStatus, visitString);

        HealthFactorLists healthFactorSet = createHealthFactorList(veteranAssessment, gov.va.escreening.vista.dto.HealthFactor.ActionSymbols.Plus);

        Set<HealthFactorVisitData> healthFactorVisitDataSet = null;

        // Use VistaClient to save to VistA
        if (healthFactorSet != null) {
            String visitDateAsFileManFormat=visitString.split(";")[1];
            VistaServiceCategoryEnum encounterServiceCategory=VistaServiceCategoryEnum.valueOf(visitString.split(";")[2]);
            if (!healthFactorSet.getCurrentHealthFactors().isEmpty()) {
                healthFactorVisitDataSet = createHealthFactorVisitDataSet(veteranAssessment, encounterServiceCategory, false, visitDateAsFileManFormat);
                saveVeteranHealthFactorsToVista(hasAppointments, vistaLinkClient, progressNoteIEN, locationIEN, false, healthFactorSet.getCurrentHealthFactors(), healthFactorHeader, healthFactorProvider, healthFactorVisitDataSet, ctxt);
            }

            if (!healthFactorSet.getHistoricalHealthFactors().isEmpty()) {
                // TODO: Need to get visit date from the historical health
                // factor prompts.
                healthFactorVisitDataSet = createHealthFactorVisitDataSet(veteranAssessment, VistaServiceCategoryEnum.A, true, visitDateAsFileManFormat);
                saveVeteranHealthFactorsToVista(hasAppointments, vistaLinkClient, progressNoteIEN, locationIEN, true, healthFactorSet.getHistoricalHealthFactors(), healthFactorHeader, healthFactorProvider, healthFactorVisitDataSet, ctxt);
            }
            ctxt.requestDone(SaveToVistaContext.PendingOperation.hf);
        }

    }

    private VistaProgressNote saveProgressNote(Long patientIEN,
                                               Long locationIEN, String visitString,
                                               VeteranAssessment veteranAssessment, VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) {
        String progressNoteContent = null;
        try {
            progressNoteContent = templateProcessorService.generateCPRSNote(veteranAssessment.getVeteranAssessmentId(), ViewType.TEXT, EnumSet.of(TemplateType.VISTA_QA));
            ctxt.addSuccess(SaveToVistaContext.PendingOperation.cprs, msg(SaveToVistaContext.MsgKey.usr_pass_cprs__gen_success));
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.cprs, Throwables.getRootCause(e).getMessage());
            return null;
        }

        VistaProgressNote vistaProgressNote = null;
        try {
            Boolean appendContent = true;
            Long visitIEN = null;
            Long titleIEN = Long.parseLong(veteranAssessment.getNoteTitle().getVistaIen());
            Date visitDate = (veteranAssessment.getDateCompleted() != null) ? veteranAssessment.getDateCompleted() : veteranAssessment.getDateUpdated();
            Object[] identifiers = {Long.parseLong(veteranAssessment.getClinician().getVistaDuz().trim()), visitDate, locationIEN, null};
            ProgressNoteParameters progressNoteParameters = new ProgressNoteParameters(patientIEN, titleIEN, locationIEN, visitIEN, visitDate, visitString, identifiers, progressNoteContent, appendContent);
            vistaProgressNote = vistaLinkClient.saveProgressNote(progressNoteParameters);
            ctxt.addSuccess(SaveToVistaContext.PendingOperation.cprs, msg(SaveToVistaContext.MsgKey.usr_pass_cprs__saved_success));
        } catch (Exception e) {
            logger.warn("Save Progress Note Failed", e);
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.cprs, Throwables.getRootCause(e).getMessage());
        }

        ctxt.requestDone(SaveToVistaContext.PendingOperation.cprs);

        return vistaProgressNote;
    }

    private MentalHealthAssessmentResult saveMentalHealthAssessments(
            Long patientIEN, VeteranAssessment veteranAssessment,
            VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) throws VistaLinkClientException {

        List<MentalHealthAssessment> mentalHealthAssessments = veteranAssessmentService.getMentalHealthAssessments(veteranAssessment.getVeteranAssessmentId());
        MentalHealthAssessmentResult mhaResults = null;

        if (!hasMhaData(mentalHealthAssessments, ctxt)) {
            return mhaResults;
        }

        for (MentalHealthAssessment mentalHealthAssessment : mentalHealthAssessments) {
            mhaResults = sendMhaToVista(veteranAssessment, patientIEN, mentalHealthAssessment, vistaLinkClient, ctxt);
            if (mhaResults != null) {
                saveMhaToDb(mentalHealthAssessment, mhaResults, veteranAssessment, ctxt);
            }
        }
        ctxt.requestDone(SaveToVistaContext.PendingOperation.sendMhaToVista);
        ctxt.requestDone(SaveToVistaContext.PendingOperation.saveMhaToDb);

        if (!ctxt.opFailed(SaveToVistaContext.PendingOperation.sendMhaToVista) &&
                !ctxt.opFailed(SaveToVistaContext.PendingOperation.saveMhaToDb)) {
            ctxt.requestDone(SaveToVistaContext.PendingOperation.mha);
        }

        return mhaResults;
    }

    private boolean hasMhaData(List<MentalHealthAssessment> mentalHealthAssessments, SaveToVistaContext ctxt) {
        if (mentalHealthAssessments.isEmpty()) {
            ctxt.requestDone(SaveToVistaContext.PendingOperation.sendMhaToVista);
            ctxt.requestDone(SaveToVistaContext.PendingOperation.saveMhaToDb);
            ctxt.requestDone(SaveToVistaContext.PendingOperation.mha);
            return false;
        }

        return true;
    }

    private void saveMhaToDb(MentalHealthAssessment mentalHealthAssessment,
                             MentalHealthAssessmentResult mhaResults,
                             VeteranAssessment veteranAssessment, SaveToVistaContext ctxt) {
        // save mental health assessment score to database here.
        Integer veteranAssessmentId = veteranAssessment.getVeteranAssessmentId();
        Long mhaSurveyId = mentalHealthAssessment.getSurveyId();
        String mhaDesc = mhaResults.getMentalHealthAssessmentResultDescription();
        try {
            veteranAssessmentService.saveMentalHealthTestResult(veteranAssessmentId, mhaSurveyId.intValue(), mhaDesc);
            ctxt.addSuccess(SaveToVistaContext.PendingOperation.saveMhaToDb, msg(SaveToVistaContext.MsgKey.usr_pass_mha__mhtr_success, veteranAssessment.getVeteran().getVeteranIen()));
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.saveMhaToDb, Throwables.getRootCause(e).getMessage());
        }

    }

    private MentalHealthAssessmentResult sendMhaToVista(
            VeteranAssessment veteranAssessment, Long patientIEN,
            MentalHealthAssessment mentalHealthAssessment,
            VistaLinkClient vistaLinkClient, SaveToVistaContext ctxt) {

        String mhaTestName = mentalHealthAssessment.getMentalHealthTestName();
        String mhaTestAnswers = mentalHealthAssessment.getMentalHealthTestAnswers();
        Long mhaReminderDialogIEN = mentalHealthAssessment.getReminderDialogIEN();

        // if mhaTestAnswers is empty or null then return null from here
        if (Strings.isNullOrEmpty(mhaTestAnswers)) {
            String warmMsg = String.format("Mental health Assessment will not be sent to Vista. " +
                            "Reason: 'MHA test answers' is left blank for 'Veteran assessment id' [%s], " +
                            "'Patient ien' [%s], 'MHA test name' [%s], 'MHA reminder dialog ien' [%s]",
                    veteranAssessment.getVeteranAssessmentId(), patientIEN, mhaTestName, mhaReminderDialogIEN);
            logger.warn(warmMsg);

            ctxt.addWarnMsg(SaveToVistaContext.PendingOperation.sendMhaToVista, warmMsg);
            return null;
        }

        // save mental health assessment test answers to VistA.

        boolean savePassed = false;

        try {
            savePassed = vistaLinkClient.saveMentalHealthAssessment(patientIEN, mhaTestName, mhaTestAnswers);
            if (savePassed) {
                ctxt.addSuccess(SaveToVistaContext.PendingOperation.sendMhaToVista, msg(SaveToVistaContext.MsgKey.usr_pass_mha__success, patientIEN));
            } else {
                ctxt.addFailedMsg(SaveToVistaContext.PendingOperation.sendMhaToVista, msg(SaveToVistaContext.MsgKey.usr_err_mha__failed, patientIEN));
                return null;
            }
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.sendMhaToVista, Throwables.getRootCause(e).getMessage());
            return null;
        }

        MentalHealthAssessmentResult assessmentResults = null;
        String staffCode = veteranAssessment.getClinician().getVistaDuz();
        try {
            vistaLinkClient.saveMentalHealthPackage(patientIEN, mhaTestName, new Date(), staffCode, mhaTestAnswers);
            ctxt.addSuccess(SaveToVistaContext.PendingOperation.sendMhaToVista, msg(SaveToVistaContext.MsgKey.usr_pass_mha__mhp_success, patientIEN));
            try {
                String dateCode = "T";
                assessmentResults = vistaLinkClient.getMentalHealthAssessmentResults(mhaReminderDialogIEN, patientIEN, mhaTestName, dateCode, staffCode, mhaTestAnswers);
                ctxt.addSuccess(SaveToVistaContext.PendingOperation.sendMhaToVista, msg(SaveToVistaContext.MsgKey.usr_pass_mha__mhar_success, patientIEN));
            } catch (Exception e) {
                ctxt.addSysErr(SaveToVistaContext.PendingOperation.sendMhaToVista, Throwables.getRootCause(e).getMessage());
            }
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.sendMhaToVista, Throwables.getRootCause(e).getMessage());
        }
        return assessmentResults;
    }

    private void saveVeteranHealthFactorsToVista(
            boolean hasAppointments,
            VistaLinkClient vistaLinkClient, Long noteIEN, Long locationIEN,
            boolean historicalHealthFactor,
            Set<gov.va.escreening.vista.dto.HealthFactor> healthFactors,
            HealthFactorHeader healthFactorHeader,
            HealthFactorProvider healthFactorProvider,
            Set<HealthFactorVisitData> healthFactorVisitDataList,
            SaveToVistaContext ctxt) throws VistaLinkClientException {

        try {
            vistaLinkClient.saveHealthFactor(hasAppointments, noteIEN, locationIEN, historicalHealthFactor, healthFactorHeader, healthFactorVisitDataList, healthFactorProvider, healthFactors);
            ctxt.addSuccess(SaveToVistaContext.PendingOperation.hf, msg(SaveToVistaContext.MsgKey.usr_pass_hf__saved_success));
        } catch (Exception e) {
            ctxt.addSysErr(SaveToVistaContext.PendingOperation.hf, Throwables.getRootCause(e).getMessage());
        }
    }

    // TODO: Re-factor to include Immunizations and Health Factors in one
    // collection as they both use the same base sequence number.
    private HealthFactorLists createHealthFactorList(
            VeteranAssessment veteranAssessment,
            gov.va.escreening.vista.dto.HealthFactor.ActionSymbols actionSymbol) {

        Set<gov.va.escreening.vista.dto.HealthFactor> historicalHealthFactors = new LinkedHashSet<gov.va.escreening.vista.dto.HealthFactor>();

        Set<gov.va.escreening.vista.dto.HealthFactor> currentHealthFactors = new LinkedHashSet<gov.va.escreening.vista.dto.HealthFactor>();

        gov.va.escreening.vista.dto.HealthFactor someHealthFactor = null;

        if (veteranAssessment != null && veteranAssessment.getHealthFactors() != null) {
            int sequenceNumber = 1;

            for (HealthFactor existingHealthFactor : veteranAssessment.getHealthFactors()) {
                someHealthFactor = createHealthFactor(existingHealthFactor, actionSymbol, sequenceNumber++);
                if (someHealthFactor.isHistoricalHealthFactor()) {
                    historicalHealthFactors.add(someHealthFactor);
                } else {
                    currentHealthFactors.add(someHealthFactor);
                }
            }
        }
        return new HealthFactorLists(currentHealthFactors, historicalHealthFactors);
    }

    private gov.va.escreening.vista.dto.HealthFactor createHealthFactor(
            HealthFactor healthFactor,
            gov.va.escreening.vista.dto.HealthFactor.ActionSymbols actionSymbol,
            int sequenceNumber) {
        String ien = healthFactor.getVistaIen();
        String name = healthFactor.getName();
        boolean historicalHealthFactor = healthFactor.getIsHistorical(); // Indicates
        String healthFactorCommentText = null;
        return new gov.va.escreening.vista.dto.HealthFactor(actionSymbol, ien, name, historicalHealthFactor, sequenceNumber, healthFactorCommentText);
    }

    private HealthFactorProvider createHealthFactorProvider(
            VeteranAssessment veteranAssessment) {
        String ien = veteranAssessment.getClinician().getVistaDuz();
        String name = veteranAssessment.getClinician().getLastName() + "," + veteranAssessment.getClinician().getFirstName();
        Boolean primaryPhysician = false; // TODO: Need to determine if the
        // escreen user is the primary physician.
        return new HealthFactorProvider(ien, name, primaryPhysician);
    }

    private Set<HealthFactorVisitData> createHealthFactorVisitDataSet(
            VeteranAssessment veteranAssessment,
            VistaServiceCategoryEnum encounterServiceCategory,
            boolean historicalHealthFactor, String visitDate) {
        Set<HealthFactorVisitData> healthFactorVisitDataSet = new LinkedHashSet<HealthFactorVisitData>();
        // Add DT Encounter Date
        healthFactorVisitDataSet.add(new VisitInfo_DT(visitDate));

        // Add PT Patient
        healthFactorVisitDataSet.add(new VisitInfo_PT(veteranAssessment.getVeteran().getVeteranIen()));

        // Add VC Encounter Service CategoryString
        healthFactorVisitDataSet.add(new VisitInfo_VC(encounterServiceCategory));

        if (historicalHealthFactor) {
            // Add PR Parent Visit IEN or Progress Note IEN, if not defined use
            // zero for IEN.
            healthFactorVisitDataSet.add(new VisitInfo_PR("0"));

            // Add OL Outside (Historical) Location
            // TODO: Need to determine if the Veteran used a outside location.
            // If so, set the outside location to addtionalData.
            String additionalData = "";
            healthFactorVisitDataSet.add(new VisitInfo_OL("0", additionalData));

        } else {
            // Add HL Encounter Location
            healthFactorVisitDataSet.add(new VisitInfo_HL(veteranAssessment.getClinic().getVistaIen()));
        }

        return healthFactorVisitDataSet;
    }

    private HealthFactorHeader createHealthFactorHeader(
            Boolean inpatientStatus, String visitString) {
        return new HealthFactorHeader(inpatientStatus, visitString);
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
}
