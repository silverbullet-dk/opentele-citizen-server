# The Format of the API Documentation
The API documentation is written using the [API Blueprint](http://apiblueprint.org/) format:

Some examples can be found [here](https://github.com/apiaryio/api-blueprint/tree/master/examples)

[Aglio](https://github.com/danielgtaylor/aglio) is used to generate an HTML version of the API documentation.

# Installing Aglio
Aglio is used to generate the HTML version of the API documentation. Aglio is installed using npm as described below:

	npm install -g aglio

# Generating HMTL API Documentation
Type the following in a command prompt/terminal in the folder of the PatientApi.md file:

	./generate-api-doc.sh
