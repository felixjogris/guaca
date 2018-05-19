guaca
=====

guaca is a redirector for [Guacamole](https://guacamole.incubator.apache.org/) that enables you to use adhoc sessions, i.e. without having to configure the connection a priori.
You can simply call ```http://localhost:8080/guaca?hostname=SOMEHOSTNAME&protocol=ssh``` to get an ssh session to SOMEHOSTNAME (assuming that your Tomcat server runs on port 8080 on localhost).
Please note that you'll need the NoAuth extension for Guacamole. See also https://ogris.de/guaca/
