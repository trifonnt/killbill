/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.notifications;

import org.jruby.embed.ScriptingContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    private final ScriptingContainer container = new ScriptingContainer();

    public void start(final BundleContext context) throws Exception {
        System.out.println("Hello world from Java");
        try {
            container.runScriptlet("puts \"Hello world from Ruby\"");
        } catch (Throwable t) {
            System.err.println(t.toString());
        }
    }

    public void stop(final BundleContext context) throws Exception {
        System.out.println("Goodbye World from Java");
        try {
            container.runScriptlet("puts \"Goodbye world from Ruby\"");
        } catch (Throwable t) {
            System.err.println(t.toString());
        }
    }
}
