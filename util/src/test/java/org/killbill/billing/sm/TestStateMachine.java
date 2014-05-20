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
import org.killbill.billing.util.sm.DefaultStateMachine;
import org.killbill.billing.util.sm.DefaultStateMachineConfig;
import org.killbill.billing.util.sm.Operation;
import org.killbill.billing.util.sm.Operation.OperationCallback;
import org.killbill.billing.util.sm.OperationResult;
import org.killbill.billing.util.sm.State;
import org.killbill.billing.util.sm.State.EnteringStateCallback;
import org.killbill.billing.util.sm.State.LeavingStateCallback;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.log4testng.Logger;

import com.google.common.io.Resources;

public class TestStateMachine {

    private Logger logger = Logger.getLogger(TestStateMachine.class);

    @Test(groups = "fast")
    public void testStateMachine() {
        try {
            final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

            Assert.assertEquals(sms.getStateMachines().length, 2);

            final DefaultStateMachine sm1 = sms.getStateMachines()[0];

            Assert.assertEquals(sm1.getStates().length, 3);
            Assert.assertEquals(sm1.getStates()[0].getName(), "AUTH_INIT");
            Assert.assertEquals(sm1.getStates()[1].getName(), "AUTH_SUCCESS");
            Assert.assertEquals(sm1.getStates()[2].getName(), "AUTH_FAILED");

            Assert.assertEquals(sm1.getOperations().length, 1);
            Assert.assertEquals(sm1.getOperations()[0].getName(), "OP_AUTHORIZE");

            Assert.assertEquals(sm1.getTransitions().length, 2);
            Assert.assertEquals(sm1.getTransitions()[0].getInitialState().getName(), "AUTH_INIT");
            Assert.assertEquals(sm1.getTransitions()[0].getOperation().getName(), "OP_AUTHORIZE");
            Assert.assertEquals(sm1.getTransitions()[0].getOperationResult(), OperationResult.SUCCESS);
            Assert.assertEquals(sm1.getTransitions()[0].getFinalState().getName(), "AUTH_SUCCESS");

            Assert.assertEquals(sm1.getTransitions()[1].getInitialState().getName(), "AUTH_INIT");
            Assert.assertEquals(sm1.getTransitions()[1].getOperation().getName(), "OP_AUTHORIZE");
            Assert.assertEquals(sm1.getTransitions()[1].getOperationResult(), OperationResult.FAILURE);
            Assert.assertEquals(sm1.getTransitions()[1].getFinalState().getName(), "AUTH_FAILED");

            final DefaultStateMachine sm2 = sms.getStateMachines()[1];

            Assert.assertEquals(sm2.getStates().length, 3);
            Assert.assertEquals(sm2.getStates()[0].getName(), "CAPTURE_INIT");
            Assert.assertEquals(sm2.getStates()[1].getName(), "CAPTURE_SUCCESS");
            Assert.assertEquals(sm2.getStates()[2].getName(), "CAPTURE_FAILED");

            Assert.assertEquals(sm2.getOperations().length, 1);
            Assert.assertEquals(sm2.getOperations()[0].getName(), "OP_CAPTURE");

            Assert.assertEquals(sm2.getTransitions().length, 2);
            Assert.assertEquals(sm2.getTransitions()[0].getInitialState().getName(), "CAPTURE_INIT");
            Assert.assertEquals(sm2.getTransitions()[0].getOperation().getName(), "OP_CAPTURE");
            Assert.assertEquals(sm2.getTransitions()[0].getOperationResult(), OperationResult.SUCCESS);
            Assert.assertEquals(sm2.getTransitions()[0].getFinalState().getName(), "CAPTURE_SUCCESS");

            Assert.assertEquals(sm2.getTransitions()[1].getInitialState().getName(), "CAPTURE_INIT");
            Assert.assertEquals(sm2.getTransitions()[1].getOperation().getName(), "OP_CAPTURE");
            Assert.assertEquals(sm2.getTransitions()[1].getOperationResult(), OperationResult.FAILURE);
            Assert.assertEquals(sm2.getTransitions()[1].getFinalState().getName(), "CAPTURE_FAILED");

            Assert.assertEquals(sms.getLinkStateMachines().length, 1);

            Assert.assertEquals(sms.getLinkStateMachines()[0].getInitialState().getName(), "AUTH_SUCCESS");
            Assert.assertEquals(sms.getLinkStateMachines()[0].getInitialStateMachine().getName(), "AUTHORIZE");

            Assert.assertEquals(sms.getLinkStateMachines()[0].getFinalState().getName(), "CAPTURE_INIT");
            Assert.assertEquals(sms.getLinkStateMachines()[0].getFinalStateMachine().getName(), "CAPTURE");

        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testStateTransition() {
        try {
            final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

            final DefaultStateMachine sm1 = sms.getStateMachines()[1];
            final State state = sm1.getState("CAPTURE_INIT");
            final Operation operation = sm1.getOperation("OP_CAPTURE");

            state.runOperation(operation,
                               new OperationCallback() {
                                   @Override
                                   public OperationResult doOperationCallback() {
                                       return OperationResult.SUCCESS;
                                   }
                               },
                               new EnteringStateCallback() {
                                   @Override
                                   public void enteringState(final State newState) {
                                       logger.info("Entering state " + newState.getName());
                                       Assert.assertEquals(newState.getName(), "CAPTURE_SUCCESS");
                                   }
                               },
                               new LeavingStateCallback() {
                                   @Override
                                   public void leavingState(final State oldState) {
                                       logger.info("Leaving state " + oldState.getName());
                                       Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                                   }
                               });

            state.runOperation(operation,
                               new OperationCallback() {
                                   @Override
                                   public OperationResult doOperationCallback() {
                                       return OperationResult.FAILURE;
                                   }
                               },
                               new EnteringStateCallback() {
                                   @Override
                                   public void enteringState(final State newState) {
                                       logger.info("Entering state " + newState.getName());
                                       Assert.assertEquals(newState.getName(), "CAPTURE_FAILED");
                                   }
                               },
                               new LeavingStateCallback() {
                                   @Override
                                   public void leavingState(final State oldState) {
                                       logger.info("Leaving state " + oldState.getName());
                                       Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                                   }
                               });


        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }
}
