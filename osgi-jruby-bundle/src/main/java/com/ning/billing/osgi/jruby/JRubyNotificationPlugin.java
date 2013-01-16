/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.jruby;

import org.jruby.javasupport.JavaEmbedUtils;

import com.ning.billing.beatrix.bus.api.ExtBusEvent;

public class JRubyNotificationPlugin extends JRubyPlugin {

    public JRubyNotificationPlugin(final String pluginMainClass, final String pluginLibdir) {
        super(pluginMainClass, pluginLibdir);
    }

    public void onEvent(final ExtBusEvent event) {
        checkValidNotificationPlugin();
        checkPluginIsRunning();

        pluginInstance.callMethod("on_event", JavaEmbedUtils.javaToRuby(getRuntime(), event));
    }
}
