package gov.va.escreening.controller;

import gov.va.escreening.dto.ae.ErrorResponse;
import gov.va.escreening.exception.*;
import gov.va.escreening.webservice.Response;
import gov.va.escreening.webservice.ResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

public abstract class RestController {
	private static final Logger logger = LoggerFactory.getLogger(RestController.class);
	
	protected void logRequest(Logger logger, HttpServletRequest request){
		logger.trace(request.getMethod() + ": "+ request.getRequestURL());
	}

	/**
	 * The preferred/standard way of returning REST responses. <br/>
	 * Besides using these response methods, you should still set an HttpStatus to very REST endpoint.<br/>
	 * Examples:<br/>
	 * {@code @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)}
	 * {@code @ResponseStatus(HttpStatus.OK)}
	 * @param responseObject
	 * @return
	 */
	protected <T> Response<T> successResponse(T responseObject){
		return new Response<>(new ResponseStatus(ResponseStatus.Request.Succeeded), responseObject);
	}
	
	/**
	 * The preferred/standard way of returning REST responses. <br/>
	 * Besides using these response methods, you should still set an HttpStatus to very REST endpoint.<br/>
	 * Examples:<br/>
	 * {@code @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)}
	 * {@code @ResponseStatus(HttpStatus.OK)}
	 * @param responseObject
	 * @return
	 */
	protected <T> Response<T> failResponse(T responseObject){
		return new Response<>(new ResponseStatus(ResponseStatus.Request.Failed), responseObject);
	}
	
	@ExceptionHandler(EntityNotFoundException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public Response<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException enfe) {
        return errorResponse(enfe.getErrorResponse());
    }

    @ExceptionHandler(FreemarkerRenderException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Response<ErrorResponse> handleFreemarkerRenderException(FreemarkerRenderException iae) {
    	ErrorResponse er;
    	if (iae instanceof ErrorResponseRuntimeException){
        	er = ((FreemarkerRenderException)iae).getErrorResponse();
        }
        else{
        	er = new ErrorResponse();
	        er.setDeveloperMessage(iae.getMessage());
	        er.addMessage("We are unable to process your request at this time. If this continues, please contact your system administrator.");
	        er.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    	
        return errorResponse(er);
    }
	
    @ExceptionHandler(Exception.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Response<ErrorResponse> handleIllegalArgumentException(Exception iae) {
        logger.error("Unexpected error", iae);
        
        ErrorResponse er;
        if (iae instanceof ErrorResponseException){
        	er = ((ErrorResponseException)iae).getErrorResponse();
        }
        else{
        	er = new ErrorResponse();
        	er.setDeveloperMessage(iae.getMessage());
            er.addMessage("An unexpected error has occured. Please contact your system administrator.");
            er.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return errorResponse(er);
    }
    
    @ExceptionHandler(EscreeningDataValidationException.class)
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public Response<ErrorResponse> handleException(EscreeningDataValidationException ex) {
		ErrorResponse errorResponse = ex.getErrorResponse().setStatus(HttpStatus.BAD_REQUEST.value());
		return errorResponse(errorResponse);
	}

    @ExceptionHandler(AssessmentEngineDataValidationException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Response<ErrorResponse> handleException(
            AssessmentEngineDataValidationException ex) {
    	
    	return errorResponse(ex.getErrorResponse());
    }
    
    @ExceptionHandler(InvalidAssessmentContextException.class)
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ResponseBody
	public Response<ErrorResponse> handleException(InvalidAssessmentContextException ex) {
		return errorResponse(ex.getErrorResponse().setStatus(HttpStatus.UNAUTHORIZED.value()));
	}
    
    @ExceptionHandler(IllegalSystemStateException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Response<ErrorResponse> handleIllegalSystemStateException(IllegalSystemStateException isse) {
		return errorResponse(isse.getErrorResponse());
    }
    
    private Response<ErrorResponse> errorResponse(ErrorResponse er){
    	logger.error(er.getLogMessage());
		return Response.createError(er);
    }
}
