FROM java:8
ADD target/cities-webapp.jar /srv/cities-webapp.jar
EXPOSE 80
CMD ["java","-jar","/srv/cities-webapp.jar"]
