(function () {
	'use strict';

	angular.module('Editors')
		.factory('AssessmentVariableManager', ['MeasureService', '$q', function(MeasureService, $q) {

			var transformations = {
				delimit: {
					name: 'delimit',
					params: {
						prefix: ',',
						lastPrefix: 'and',
						suffix: '""',
						includeSuffixAtEnd: true,
						defaultValue: ''
					}
				},
				yearsFromDate: { name: 'yearsFromDate' },
				delimitedMatrixQuestions: {
					name: 'delimitedMatrixQuestions',
					params: {
						lastPrefix: 'and'
					}
				},
				numberOfEntries: {
					name: 'numberOfEntries'
				},
				delimitTableField:	{
					name: 'delimitTableField',
					params: {
						prefix: ',',
						lastPrefix: 'and',
						suffix: '""',
						includeSuffixAtEnd: true,
						defaultValue: ''
					}
				}
			};

			function setTransformations(av) {
				var deferred = $q.defer();

				if (av.measureTypeId === 1) {
					// Get the validations for freetext
					MeasureService.one(av.measureId).getList('validations').then(function (validations) {
						av.setTransformations(validations);
						deferred.resolve(av.transformations);
					});
				}

				if (av.measureTypeId === 3) {
					// Get the answer list for multi or single select questions
					MeasureService.one(av.measureId).getList('answers').then(function (answers) {
						av.setTransformations(answers);
						deferred.resolve(av.transformations);
					});
				}
				else {
					av.setTransformations();
					deferred.resolve(av.transformations);
				}

				return deferred.promise;
			}

			function setValidations(map, validation) {

				switch(validation.validateId) {
					case 1:
						map[validation.value] = validation.value;
						break;
					case 4:
						map.minLength = validation.value || 0;
						break;
					case 5:
						map.maxLength = validation.value || 150;
						break;
					case 6:
						map.minValue = validation.value;
						break;
					case 7:
						map.maxValue = validation.value;
						break;
					case 9:
						map.exactLength = validation.value;
						break;
				}

				return map;
			}

			return {
				setTransformations: setTransformations,
				setValidations: setValidations
			};

		}]
	);
})();
