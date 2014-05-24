/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm;

import java.util.UUID;

import org.killbill.automaton.Operation;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public abstract class DirectPaymentEnteringStateCallback implements EnteringStateCallback {

    private final Logger logger = LoggerFactory.getLogger(DirectPaymentEnteringStateCallback.class);

    protected final DirectPaymentAutomatonDAOHelper daoHelper;
    protected final DirectPaymentStateContext directPaymentStateContext;

    protected DirectPaymentEnteringStateCallback(final DirectPaymentAutomatonDAOHelper daoHelper, final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.directPaymentStateContext = directPaymentStateContext;
    }

    @Override
    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        logger.debug("Entering state {} with result {}", newState.getName(), operationResult);

        final PaymentInfoPlugin paymentInfoPlugin = directPaymentStateContext.getPaymentInfoPlugin();
        final UUID directPaymentTransactionId = directPaymentStateContext.getDirectPaymentTransactionModelDao().getId();

        // Check for illegal states (should never happen)
        Preconditions.checkState(OperationResult.EXCEPTION.equals(operationResult) || paymentInfoPlugin != null);
        Preconditions.checkState(directPaymentTransactionId != null);

        final PaymentStatus paymentStatus = processOperationResult(operationResult);
        daoHelper.processPaymentInfoPlugin(paymentStatus, paymentInfoPlugin, directPaymentTransactionId, newState.getName());
    }

    protected PaymentStatus processOperationResult(final OperationResult operationResult) {
        switch (operationResult) {
            case PENDING:
                return PaymentStatus.PENDING;
            case SUCCESS:
                return PaymentStatus.SUCCESS;
            case FAILURE:
                return PaymentStatus.PAYMENT_FAILURE_ABORTED;
            case EXCEPTION:
            default:
                return PaymentStatus.PLUGIN_FAILURE_ABORTED;
        }
    }
}
