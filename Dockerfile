FROM java:8
ADD target/milo-webapp.jar /srv/milo-webapp.jar
EXPOSE 80
CMD ["java","-jar","/srv/milo-webapp.jar"]
