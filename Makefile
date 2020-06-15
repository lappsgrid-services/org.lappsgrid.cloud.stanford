VERSION=$(shell cat VERSION)
WAR=stanford-soap-$(VERSION).war
DOCKER=src/main/docker/$(WAR)
TARGET=target/$(WAR)
REPO=docker.lappsgrid.org
GROUP=soap
IMAGE=stanford
NAME=$(GROUP)-$(IMAGE)
TAG=$(GROUP)/$(IMAGE):$(VERSION)

CREDS="-e RABBIT_USERNAME=$(RABBIT_USERNAME) -e RABBIT_PASSWORD=$(RABBIT_PASSWORD)"

war:
	mvn package

clean:
	mvn clean
	if [ -e $(DOCKER) ] ; then rm $(DOCKER) ; fi

docker:
	@if [ ! -e $(DOCKER) ] ; then cp $(TARGET) $(DOCKER) ; fi
	@if [ $(TARGET) -nt $(DOCKER) ] ; then cp $(TARGET) $(DOCKER) ; fi
	cd src/main/docker && docker build -t $(GROUP)/$(IMAGE) .
	
start:
	docker run -d -p 8080:8080 --name $(NAME) $(CREDS) $(GROUP)/$(IMAGE)

test:
	src/test/lsd/integration.lsd

stop:
	docker rm -f $(NAME)

push:
	docker tag $(GROUP)/$(IMAGE) $(REPO)/$(GROUP)/$(IMAGE)
	docker push $(REPO)/$(GROUP)/$(IMAGE)

tag:
	docker tag $(GROUP)/$(IMAGE) $(REPO)/$(GROUP)/$(IMAGE):$(VERSION)
	docker push $(REPO)/$(GROUP)/$(IMAGE):$(VERSION)

all: clean war docker push

#update:
#	curl -X POST http://129.114.17.83:9000/api/webhooks/d735387d-a6c1-4303-be3f-3b8d55de4f48


	
