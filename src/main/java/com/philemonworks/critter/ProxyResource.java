package com.philemonworks.critter;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import com.philemonworks.critter.action.Forward;
import com.philemonworks.critter.action.Respond;
import com.philemonworks.critter.action.ResponseBody;
import com.philemonworks.critter.rule.Rule;
import com.philemonworks.critter.rule.RuleContext;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.spi.container.ContainerRequest;

@Path("/")
@Consumes("*/*")
public class ProxyResource {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyResource.class);
    
    @Inject
    private TrafficManager trafficManager;
    @Inject
    private HttpClient httpClient;

    @GET
    public Response handleGet(@Context HttpContext httpContext) {
        return this.performActionsFor(httpContext, new HttpGet());
    }

    @POST
    public Response handlePost(@Context HttpContext httpContext) {
        return this.performActionsFor(httpContext, new HttpPost());
    }

    @PUT
    public Response handlePut(@Context HttpContext httpContext) {
        return this.performActionsFor(httpContext, new HttpPut());
    }

    @DELETE
    public Response handleDelete(@Context HttpContext httpContext) {
        return this.performActionsFor(httpContext, new HttpDelete());
    }

    private Response performActionsFor(HttpContext httpContext, HttpRequestBase requestBase) {        
        ContainerRequest containerRequest = (ContainerRequest) httpContext.getRequest();
        URI destinationOrNull = Utils.extractForwardURIFrom((URI)containerRequest.getProperties().get(ProxyFilter.PROXY_FILTER_URI));
        String label = requestBase.getMethod() + " " + destinationOrNull;
        LOG.trace(label);
        Monitor proxyMon = MonitorFactory.start(destinationOrNull == null ? "/" : destinationOrNull.getHost());
        
        RuleContext ruleContext = new RuleContext();
        ruleContext.httpClient = httpClient;
        ruleContext.httpContext = httpContext;
        ruleContext.forwardMethod = requestBase;

        if (null == destinationOrNull) {
            new ResponseBody()
        } else {        
            Rule rule = this.trafficManager.detectRule(httpContext);
            if (null == rule) {
                Monitor mon = MonitorFactory.start("--critter.passthrough");
                new Forward().perform(ruleContext);
                new Respond().perform(ruleContext);
                mon.stop();
            } else {
                Monitor mon = MonitorFactory.start("--critter.rule."+rule.id);
                this.trafficManager.performRule(rule, ruleContext);
                mon.stop();
            }
        }
        
        proxyMon.stop();        
        return ruleContext.proxyResponse;
    }
}