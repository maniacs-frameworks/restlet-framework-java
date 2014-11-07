/**
 * Copyright 2005-2014 Restlet
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: Apache 2.0 or LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL
 * 1.0 (the "Licenses"). You can select the license that you prefer but you may
 * not use this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the Apache 2.0 license at
 * http://www.opensource.org/licenses/apache-2.0
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://restlet.com/products/restlet-framework
 * 
 * Restlet is a registered trademark of Restlet S.A.S.
 */

package org.restlet.ext.swagger;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.engine.cors.CorsResponseHelper;
import org.restlet.ext.apispark.internal.conversion.swagger.v1_2.SwaggerTranslator;
import org.restlet.ext.apispark.internal.conversion.swagger.v1_2.model.ApiDeclaration;
import org.restlet.ext.apispark.internal.conversion.swagger.v1_2.model.ResourceListing;
import org.restlet.ext.apispark.internal.introspection.IntrospectorPlugin;
import org.restlet.ext.apispark.internal.introspection.jaxrs.JaxRsIntrospector;
import org.restlet.ext.apispark.internal.model.Definition;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.routing.Router;

import javax.ws.rs.core.Application;
import java.util.ArrayList;
import java.util.List;

/**
 * Restlet that generates Swagger documentation in the format defined by the
 * swagger-spec project for a JaxRS application<br>
 * It helps to generate the high level documentation for the whole API (set by
 * calling {@link #setApplication(Application)} or
 * {@link #setApiInboundRoot(Application)} methods, and the documentation for each
 * resource.<br>
 * By default it instrospects the JaxRs Application classes and singletons.<br>
 * Use the {@link org.restlet.ext.swagger.SwaggerSpecificationRestlet} restlet for Restlet
 * applications.
 *
 * <p>
 * Usage example:
 * <pre>
 * new SwaggerSpecificationRestlet()
 *      .setApiInboundRoot(this)
 *      .setBasePath("http://myapp.com/api/v1")
 *      .addIntrospectorPlugin(new SwaggerAnnotationIntrospectorPlugin()) //provided by apispark-swagger-annotations-xx extension
 *      .attach(baseRouter);
 * </pre>
 * </p>

 * @author Thierry Boileau
 * @see <a href="http://github.com/wordnik/swagger-ui">Swagger UI (github)</a>
 * @see <a href="http://petstore.swagger.wordnik.com">Petstore sample application of Swagger-UI</a>
 * @see <a href="http://helloreverb.com/developers/swagger">Swagger Developper page</a>
 */
public class JaxRsApplicationSwaggerSpecificationRestlet extends Restlet {

    /**
     * The version of the Swagger specification.
     *  Default is {@link SwaggerTranslator#SWAGGER_VERSION}
     */
    private String swaggerVersion = SwaggerTranslator.SWAGGER_VERSION;

    /** The version of the API. */
    private String apiVersion;

    /** The Application to describe. */
    private Application application;

    /** The base path of the API. */
    private String basePath;

    /** The base reference of the API. */
    private Reference baseRef;

    /** The RWADef of the API. */
    private Definition definition;

    /** List of additional introspector plugins to use */
    private List<IntrospectorPlugin> introspectorPlugins = new ArrayList<IntrospectorPlugin>();

    /** Helper used to add CORS response headers */
    private CorsResponseHelper corsResponseHelper = new CorsResponseHelper();

    /**
     * Default constructor.<br>
     */
    public JaxRsApplicationSwaggerSpecificationRestlet() {
        this(null);
    }

    /**
     * Constructor.<br>
     *
     * @param context
     *            The context.
     */
    public JaxRsApplicationSwaggerSpecificationRestlet(Context context) {
        super(context);
    }

    /**
     * Returns the Swagger documentation of a given resource, also known as
     * "API Declaration" in Swagger vocabulary.
     *
     * @param category
     *            The category of the resource to describe.
     * @return The representation of the API declaration.
     */
    public Representation getApiDeclaration(String category) {
        ApiDeclaration apiDeclaration = SwaggerTranslator.getApiDeclaration(category, getDefinition());
        apiDeclaration.setSwaggerVersion(swaggerVersion);
        return new JacksonRepresentation<>(apiDeclaration);
    }

    /**
     * Returns the version of the Swagger specification.
     *  Default is {@link SwaggerTranslator#SWAGGER_VERSION}
     *
     * @return The version of the Swagger specification.
     */
    public String getSwaggerVersion() {
        return swaggerVersion;
    }

    /**
     * Returns the API's version.
     * 
     * @return The API's version.
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Returns the base path of the API.
     * 
     * @return The base path of the API.
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Returns the application's definition.
     * 
     * @return The application's definition.
     */
    private synchronized Definition getDefinition() {
        if (definition == null) {
            synchronized (JaxRsApplicationSwaggerSpecificationRestlet.class) {
                definition = JaxRsIntrospector.getDefinition(application,
                        baseRef,
                        introspectorPlugins);
                // This data seems necessary for Swagger codegen.
                if (definition.getVersion() == null) {
                    definition.setVersion(apiVersion != null ? apiVersion : "1.0");
                }
            }
        }

        return definition;
    }

    /**
     * Returns the representation of the whole resource listing of the
     * Application.
     *
     * @return The representation of the whole resource listing of the
     *         Application.
     */
    public Representation getResourceListing() {
        ResourceListing resourcelisting = SwaggerTranslator.getResourcelisting(getDefinition());
        resourcelisting.setSwaggerVersion(swaggerVersion);
        return new JacksonRepresentation<>(resourcelisting);
    }

    @Override
    public void handle(Request request, Response response) {
        super.handle(request, response);

        // CORS support for Swagger-UI
        corsResponseHelper.addCorsResponseHeaders(request, response);

        if (Method.GET.equals(request.getMethod())) {
            Object resource = request.getAttributes().get("resource");

            if (resource instanceof String) {
                response.setEntity(getApiDeclaration((String) resource));
            } else {
                response.setEntity(getResourceListing());
            }
        } else {
            response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        }

    }

    /**
     * Defines two routes, one for the high level "Resource listing" (by default
     * "/api-docs"), and the other one for the "API declaration". The second
     * route is a sub-resource of the first one, defined with the path variable
     * "resource" (ie "/api-docs/{resource}").
     *
     * @param router
     *            The router on which defining the new route.
     *
     * @see #attach(org.restlet.routing.Router, String) to attach it with a custom path
     */
    public void attach(Router router) {
        attach(router, "/api-docs");
    }

    /**
     * Defines two routes, one for the high level "Resource listing", and the
     * other one for the "API declaration". The second route is a sub-resource
     * of the first one, defined with the path variable "resource".
     *
     * @param router
     *            The router on which defining the new route.
     * @param path
     *            The root path of the documentation Restlet.
     *
     * @see #attach(org.restlet.routing.Router) to attach it with the default path
     */
    public void attach(Router router, String path) {
        router.attach(path, this);
        router.attach(path + "/{resource}", this);
    }

    /**
     * Sets the root Restlet for the given application.
     * 
     * @param application
     *            The application.
     */
    public JaxRsApplicationSwaggerSpecificationRestlet setApiInboundRoot(Application application) {
        this.application = application;
        return this;
    }

    /**
     * Sets the root Restlet for the given application.
     * 
     * @param application
     *            The application
     */
    public JaxRsApplicationSwaggerSpecificationRestlet setApplication(Application application) {
        this.application = application;
        return this;
    }

    /**
     * Add an introspector plugin to default introspector
     *
     * @param introspectorPlugin
     *          Introspector Plugin to add
     *
     */
    public JaxRsApplicationSwaggerSpecificationRestlet addIntrospectorPlugin(IntrospectorPlugin introspectorPlugin) {
        introspectorPlugins.add(introspectorPlugin);
        return this;
    }

    /**
     * Sets the version of the Swagger specification.
     *
     * @param swaggerVersion
     *            The version of the Swagger specification.
     */
    public JaxRsApplicationSwaggerSpecificationRestlet setSwaggerVersion(String swaggerVersion) {
        this.swaggerVersion = swaggerVersion;
        return this;
    }

    /**
     * Sets the API's version.
     * 
     * @param apiVersion
     *            The API version.
     */
    public JaxRsApplicationSwaggerSpecificationRestlet setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    /**
     * Sets the base path of the API.
     * 
     * @param basePath
     *            The base path of the API
     */
    public JaxRsApplicationSwaggerSpecificationRestlet setBasePath(String basePath) {
        this.basePath = basePath;
        //Process basepath and check validity
        this.baseRef = basePath != null ? new Reference(basePath) : null;
        return this;
    }

}