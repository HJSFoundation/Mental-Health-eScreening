/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

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
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 *
 * @author jocchiuzzo
 */
@Entity
@Table(name = "template")
@NamedQueries({
    @NamedQuery(name = "Template.findAll", query = "SELECT t FROM Template t")})
public class Template implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "template_id")
    private Integer templateId;
    @Basic(optional = false)
    @Column(name = "name")
    private String name;
    @Column(name = "description")
    private String description;
    @Lob
    @Column(name = "template_file")
    private String templateFile;
    @Column(name="json_file")
    private String jsonFile;
    @Basic(optional = false)
    @Column(name = "date_created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "templateId", orphanRemoval=true)
    private List<VariableTemplate> variableTemplateList;
    @JoinColumn(name = "template_type_id", referencedColumnName = "template_type_id")
    @ManyToOne(optional = false)
    private TemplateType templateType;
    @Column(name = "is_graphical")
    private boolean isGraphical;
    @Column(name = "graph_params")
    private String graphParams;
    @Column(name="date_modified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedDate;

    public Template() {
	this.dateCreated = new Date();
    }

    public Template(Integer templateId) {
        this.templateId = templateId;
	this.dateCreated = new Date();
    }

    public Template(Integer templateId, String name, String description, String templateFile) {
        this.templateId = templateId;
        this.name = name;
        this.templateFile = templateFile;
	this.dateCreated = new Date();
    }

    public Integer getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Integer templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    public void setTemplateFile(String templateFile) {
        this.templateFile = templateFile;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public List<VariableTemplate> getVariableTemplateList() {
        return variableTemplateList;
    }

    public void setVariableTemplateList(List<VariableTemplate> variableTemplateList) {
        this.variableTemplateList = variableTemplateList;
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }
    
    public Boolean getIsGraphical(){
    	return isGraphical;
    }
    
    public void setIsGraphical(Boolean isGraphical){
    	this.isGraphical = isGraphical;
    }
    
    public String getJsonFile() {
		return jsonFile;
	}

	public void setJsonFile(String jsonFile) {
		this.jsonFile = jsonFile;
	}

	public Date getModifiedDate() {
		return modifiedDate;
	}

	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}
	
	public String getGraphParams() {
        return graphParams;
    }

    public void setGraphParams(String graphParams) {
        this.graphParams = graphParams;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (templateId != null ? templateId.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Template)) {
            return false;
        }
        Template other = (Template) object;
        if ((this.templateId == null && other.templateId != null) || (this.templateId != null && !this.templateId.equals(other.templateId))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "gov.va.escreening.entity.Template[ templateId=" + templateId + " ]";
    }
    
}
