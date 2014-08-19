package gov.va.escreening.controller.dashboard;

import gov.va.escreening.constants.TemplateConstants;
import gov.va.escreening.constants.TemplateConstants.TemplateType;
import gov.va.escreening.constants.TemplateConstants.ViewType;
import gov.va.escreening.dto.ae.ErrorResponse;
import gov.va.escreening.exception.ErrorResponseException;
import gov.va.escreening.security.CurrentUser;
import gov.va.escreening.security.EscreenUser;
import gov.va.escreening.service.VeteranAssessmentService;
import gov.va.escreening.templateprocessor.TemplateProcessorService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/dashboard")
public class AssessmentSummaryRestController {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentSummaryRestController.class);

    private VeteranAssessmentService veteranAssessmentService;
    private TemplateProcessorService templateProcessorService;

    @Autowired
    public void setTemplateProcessorService(TemplateProcessorService templateProcessorService) {
        this.templateProcessorService = templateProcessorService;
    }

    @Autowired
    public void setVeteranAssessmentService(VeteranAssessmentService veteranAssessmentService) {
        this.veteranAssessmentService = veteranAssessmentService;
    }

    @RequestMapping(value = "/assessmentSummary/assessments/{veteranAssessmentId}/healthFactorTitles", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    @ResponseBody
    public List<String> getHealthFactorTitleList(@PathVariable Integer veteranAssessmentId,
            @CurrentUser EscreenUser escreenUser) {

        logger.debug("getHealthFactorTitleList");

        // Call service class here.

        List<String> healthFactorTitleList = veteranAssessmentService.getHealthFactorReport(veteranAssessmentId);

        if (healthFactorTitleList != null) {
            logger.debug(healthFactorTitleList.toString());

            List<String> formattedList = new ArrayList<String>();

            for (int i = 0; i < healthFactorTitleList.size(); ++i) {
                formattedList.add(healthFactorTitleList.get(i) + "<br/>");
            }

            return formattedList;
        }

        return healthFactorTitleList;
    }

    @RequestMapping(value = "/assessmentSummary/assessments/{veteranAssessmentId}/cprsNote", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    @ResponseBody
    public String getCprsNote(@PathVariable Integer veteranAssessmentId, @CurrentUser EscreenUser escreenUser) {

        logger.debug("getCprsNote");
        String progressNoteContent = null;

        try {
            progressNoteContent = templateProcessorService.generateCPRSNote(veteranAssessmentId, ViewType.HTML, EnumSet.of(TemplateType.ASSESS_SCORE_TABLE));
        }
        catch (Exception e) {
            if(e instanceof ErrorResponseException){
                ErrorResponse error = ((ErrorResponseException)e).getErrorResponse();
                logger.error(error.getLogMessage());
                //TODO: we should pass the ErrorResponse instead of a string
                progressNoteContent = error.getUserMessage("<br/>");
            }
            else{
                logger.error("Exception thrown trying to generate CPRS Note: " + e, e);
                progressNoteContent = "An unexpected error occured while generating the CPRS Note. Please try again and if the problem persists, contact the technical administrator.";
            }
        }
        
        logger.debug("Returrning note:\n{}", progressNoteContent);

        return progressNoteContent;
    }
    
    
    @RequestMapping(value = "/assessmentSummary/assessments/{veteranAssessmentId}/veteranSummary", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    @ResponseBody
    public String getVeteranSummary(@PathVariable Integer veteranAssessmentId, @CurrentUser EscreenUser escreenUser) {

        logger.debug("veteranSummary");
        String progressNoteContent = null;

        try {
            progressNoteContent = templateProcessorService.generateVeteranPrintout(veteranAssessmentId);
        }
        catch (Exception e) {
            if(e instanceof ErrorResponseException){
                ErrorResponse error = ((ErrorResponseException)e).getErrorResponse();
                logger.error(error.getLogMessage());
                //TODO: we should pass the ErrorResponse instead of a string
                progressNoteContent = error.getUserMessage("<br/>");
            }
            else{
                logger.error("Exception thrown trying to generate Veteran Summary: " + e, e);
                progressNoteContent = "An unexpected error occured while generating the Veteran Summary. Please try again and if the problem persists, contact the technical administrator.";
            }
        }
        
        logger.debug("Returrning note:\n{}", progressNoteContent);

        return progressNoteContent;
    }

}
