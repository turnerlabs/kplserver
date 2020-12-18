all: help

.PHONY: help
help: Makefile
	@echo
	@echo " Choose a make command to run"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo

## build: build jar and deps
.PHONY: build
build:
	mvn clean install

## start: build and start local server
.PHONY: start
start: build
	PORT=3000 AWS_DEFAULT_REGION=us-east-1 java -jar target/app.jar
