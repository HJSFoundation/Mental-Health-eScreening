package gov.va.escreening.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "program")
@NamedQueries({
        @NamedQuery(name = "Program.findAll", query = "SELECT p FROM Program p")})
public class Program implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "program_id")
    private Integer programId;

    @Basic(optional = false)
    @Column(name = "name")
    private String name;

    @Basic(optional = false)
    @Column(name = "is_disabled")
    private boolean isDisabled;

    @Basic(optional = false)
    @Column(name = "date_created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "program")
    private List<ClinicProgram> clinicProgramList;

    @OneToMany(mappedBy = "program")
    private List<VeteranAssessment> veteranAssessmentList;

    @OneToMany(mappedBy = "program")
    private List<UserProgram> userProgramList;

    @OneToMany(mappedBy = "program")
    private List<NoteTitleMap> noteTitleMapList;

    @OneToMany(mappedBy = "program")
    private List<ExportLog> exportLogList;

    public Program() {
	this.dateCreated = new Date();
    }

    public Program(Integer programId) {
        this.programId = programId;
	this.dateCreated = new Date();
    }

    public Program(Integer programId, String name, boolean isDisabled, Date dateCreated) {
        this.programId = programId;
        this.name = name;
        this.isDisabled = isDisabled;
        this.dateCreated = dateCreated;
    }

    public Integer getProgramId() {
        return programId;
    }

    public void setProgramId(Integer programId) {
        this.programId = programId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public List<ClinicProgram> getClinicProgramList() {
        return clinicProgramList;
    }

    public void setClinicProgramList(List<ClinicProgram> clinicProgramList) {
        this.clinicProgramList = clinicProgramList;
    }

    public List<VeteranAssessment> getVeteranAssessmentList() {
        return veteranAssessmentList;
    }

    public void setVeteranAssessmentList(List<VeteranAssessment> veteranAssessmentList) {
        this.veteranAssessmentList = veteranAssessmentList;
    }

    public List<UserProgram> getUserProgramList() {
        return userProgramList;
    }

    public void setUserProgramList(List<UserProgram> userProgramList) {
        this.userProgramList = userProgramList;
    }

    public List<NoteTitleMap> getNoteTitleMapList() {
        return noteTitleMapList;
    }

    public void setNoteTitleMapList(List<NoteTitleMap> noteTitleMapList) {
        this.noteTitleMapList = noteTitleMapList;
    }

    public List<ExportLog> getExportLogList() {
        return exportLogList;
    }

    public void setExportLogList(List<ExportLog> exportLogList) {
        this.exportLogList = exportLogList;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (programId != null ? programId.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Program)) {
            return false;
        }
        Program other = (Program) object;
        if ((this.programId == null && other.programId != null)
                || (this.programId != null && !this.programId.equals(other.programId))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "gov.va.escreening.entity.Program[ programId=" + programId + " ]";
    }

    public void updateClinics(List<Clinic> updatedClinics) {
    }
}
