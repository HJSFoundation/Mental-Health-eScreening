/**
 * Angular service factory method for Template Types.
 *
 * @author Tonté Pouncil
 */
angular.module('EscreeningDashboardApp.services.templateType', ['restangular'])
    .factory('TemplateTypeService', ['Restangular', 'TemplateType', function (Restangular, TemplateType){
        "use strict";

        var currentTemplateTypes = [];
        var currentTemplateType = null;
        
        var restAngular = Restangular.withConfig(function(Configurer) {
                Configurer.setBaseUrl('/escreeningdashboard/dashboard');
                Configurer.setRequestSuffix('.json');
            }),
            service = restAngular.service("services/templateTypes");

        restAngular.extendModel("services/templateTypes", function(model) {
            return angular.extend(model, TemplateType);
        });
        
        
        var regTypes = function($scope, templateTypes){
            $scope.templateTypes = templateTypes;
            $scope.$watch('templateTypes', function(newVal, oldVal){
                console.log("Updating template type list");
                currentTemplateTypes = newVal;
            }, true);
        };
 
        // Expose the public TemplateTypeService API to the rest of the application.
        //return service;
        return {
            /**
             * Will retrieve the list of template types given the query parameter or return what was last 
             * list queried if queryParam is null.  If queryParam is empty then the server will be queried.
             */
            getTemplateTypes: function (queryParams) {
                if(Object.isDefined(queryParams)){
                    return service.getList(queryParams).then(
                            function(templateTypes){
                                currentTemplateTypes = templateTypes;
                                return templateTypes;
                            });
                }
                return this.currentTemplateTypes;
            },
            /**
             * Connects the given types to the give scope and sets up a $watch for changes so
             * the state is kept up to date.  If this is not called in a controller, then changes 
             * will not be reflected when getTemplateTypes is called.
             */
            registerTypes : regTypes,
            
            /**
             * To track the currently selected template type.
             */
            setSelectedType : function(currentTemplateType){ 
                this.currentTemplateType = currentTemplateType; 
            },
            getSelectedType : function(){
                return this.currentTemplateType; 
            }
        }
    }]);