#!/usr/bin/python

import sys, cgi, xml.dom.minidom

CONFIG_FILE = "/etc/guacamole/noauth-config.xml"

def errorpage(error):
    print "Content-Type: text/plain"
    print "Content-Length: %d" % (len(error))
    print
    print error
    sys.exit(-1)

form = cgi.FieldStorage()
if not "proto" in form or not "host" in form:
    errorpage("Need proto and host")
proto = form["proto"]
host = form["host"]
if "port" in form:
    port = form["port"]
elif proto == "vnc":
    port = "5900"
elif proto == "ssh":
    port = "22"
elif proto == "rdp":
    port = "3389"
else:
    errorpage("No default port for this protocol")

configs = []
xml1 = xml.dom.minidom.parse(CONFIG_FILE)
for node in [node for node in xml1.documentElement.childNodes
             if node.nodeType == node.ELEMENT_NODE and
             node.localName == "config"]:
    config = {"name": node.getAttribute("name"),
              "protocol": node.getAttribute("protocol")}
    params = {}
    for node2 in [node2 for node2 in node.childNodes
                  if node2.nodeType == node2.ELEMENT_NODE and
                  node2.localName == "param"]:
        params[node2.getAttribute("name")] = node2.getAttribute("value") 
    config["params"] = params
    configs.append(config)

print configs

xml2 = xml.dom.minidom.getDOMImplementation().createDocument(None, "configs", None)
docElem = xml2.documentElement
for config in configs:
    cfgElem = xml2.createElement("config")
    cfgElem.setAttribute("name", config["name"])
    cfgElem.setAttribute("protocol", config["protocol"])
    docElem.appendChild(cfgElem)
    for (name, value) in config["params"].iteritems():
        paramElem = xml2.createElement("param")
        cfgElem.appendChild(paramElem)
        paramElem.setAttribute("name", name)
        paramElem.setAttribute("value", value)

print xml2.toprettyxml()
