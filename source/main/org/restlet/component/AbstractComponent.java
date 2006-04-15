/*
 * Copyright 2005-2006 J�r�me LOUVEL
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet.component;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.restlet.AbstractRestlet;
import org.restlet.Restlet;
import org.restlet.RestletCall;
import org.restlet.connector.Client;
import org.restlet.connector.Server;

/**
 * Abstract component implementation.
 */
public abstract class AbstractComponent extends AbstractRestlet implements Component
{
	/** The initialization parameters. */
	protected Map<String, String> initParameters;
	
	/** The component name. */
   protected String name;

   /** The map of client connectors. */
   protected Map<String, Client> clients;

   /** The map of server connectors. */
   protected Map<String, Server> servers;

   /**
    * Constructor.
    * @param name The component name.
    */
   public AbstractComponent(String name)
   {
   	this.initParameters = null;
      this.name = name;
      this.clients = new TreeMap<String, Client>();
      this.servers = new TreeMap<String, Server>();
   }

	/**
	 * Returns a modifiable map of initialization parameters
	 * @return A modifiable map of initialization parameters
	 */
	public Map<String, String> getInitParameters()
	{
		if(this.initParameters == null) this.initParameters = new TreeMap<String, String>();
		return this.initParameters;
	}

   /**
    * Returns the name of this REST element.
    * @return The name of this REST element.
    */
   public String getName()
   {
      return this.name;
   }

   /**
    * Adds a server connector to this component.
    * @param server The server connector to add.
    * @return The server connector added.
    */
   public Server addServer(Server server)
   {
      this.servers.put(server.getName(), server);
      return server;
   }

   /**
    * Removes a server connector from this component.
    * @param name The name of the server connector to remove.
    */
   public void removeServer(String name)
   {
      this.servers.remove(name);
   }

   /**
    * Adds a client connector to this component.
    * @param client The client connector to add.
    * @return The client connector added.
    */
   public Client addClient(Client client)
   {
      this.clients.put(client.getName(), client);
      return client;
   }

   /**
    * Removes a client connector from this component.
    * @param name The name of the client connector to remove.
    */
   public void removeClient(String name)
   {
      this.clients.remove(name);
   }

   /**
    * Calls a client connector.
    * @param name The name of the client connector.
    * @param call The call to handle.
    */
   public void callClient(String name, RestletCall call) throws IOException
   {
      Restlet connector = (Restlet)this.clients.get(name);

      if(connector == null)
      {
         throw new IOException("Client connector \"" + name + "\" couldn't be found.");
      }
      else
      {
         connector.handle(call);
      }
   }

   /**
    * Returns the description of this REST element.
    * @return The description of this REST element.
    */
   public String getDescription()
   {
      return "Abstract component";
   }

   /**
    * Start hook. Starts all client and server connectors.
    */
   public void start() throws Exception
   {
      for(Iterator iter = this.clients.keySet().iterator(); iter.hasNext();)
      {
         this.clients.get(iter.next()).start();
      }
      for(Iterator iter = this.servers.keySet().iterator(); iter.hasNext();)
      {
         this.servers.get(iter.next()).start();
      }

      super.start();
   }

   /**
    * Stop hook. Stops all client and server connectors.
    */
   public void stop() throws Exception
   {
      for(Iterator iter = this.clients.keySet().iterator(); iter.hasNext();)
      {
         this.clients.get(iter.next()).stop();
      }
      for(Iterator iter = this.servers.keySet().iterator(); iter.hasNext();)
      {
         this.servers.get(iter.next()).stop();
      }

      super.stop();
   }

}
