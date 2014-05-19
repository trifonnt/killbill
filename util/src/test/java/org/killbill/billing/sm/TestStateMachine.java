/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.sm;

import org.killbill.billing.util.config.catalog.XMLLoader;
import org.killbill.billing.util.sm.DefaultStateMachineConfig;
import org.killbill.billing.util.sm.OperationResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

public class TestStateMachine {

    @Test(groups = "fast")
    public void testStateMachine() {
        try {
            final DefaultStateMachineConfig sm = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

            Assert.assertEquals(sm.getStates().length, 3);
            Assert.assertEquals(sm.getStates()[0].getName(), "INIT");
            Assert.assertEquals(sm.getStates()[1].getName(), "SUCCESS");
            Assert.assertEquals(sm.getStates()[2].getName(), "FAILED");

            Assert.assertEquals(sm.getOperations().length, 3);
            Assert.assertEquals(sm.getOperations()[0].getName(), "Authorize");
            Assert.assertEquals(sm.getOperations()[1].getName(), "Capture");
            Assert.assertEquals(sm.getOperations()[2].getName(), "Payment");

            Assert.assertEquals(sm.getTransitions().length, 2);
            Assert.assertEquals(sm.getTransitions()[0].getInitialState().getName(), "INIT");
            Assert.assertEquals(sm.getTransitions()[0].getOperation().getName(), "Authorize");
            Assert.assertEquals(sm.getTransitions()[0].getOperationResult(), OperationResult.SUCCESS);
            Assert.assertEquals(sm.getTransitions()[0].getFinalState().getName(), "SUCCESS");

            Assert.assertEquals(sm.getTransitions()[1].getInitialState().getName(), "INIT");
            Assert.assertEquals(sm.getTransitions()[1].getOperation().getName(), "Authorize");
            Assert.assertEquals(sm.getTransitions()[1].getOperationResult(), OperationResult.FAILURE);
            Assert.assertEquals(sm.getTransitions()[1].getFinalState().getName(), "FAILED");

        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }
}
