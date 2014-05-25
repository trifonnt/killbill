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

package org.killbill.billing.payment.core.sm;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;

public class RetryAuthorizeOperationCallback extends RetryOperationCallback {

    private final DirectPaymentStateContext stateContext;

    public RetryAuthorizeOperationCallback(final DirectPaymentStateContext stateContext, final DirectPaymentProcessor directPaymentProcessor) {
        super(directPaymentProcessor);
        this.stateContext = stateContext;
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        // STEPH retrieve plugin ?
        RetryPluginApi plugin = null;

        InternalCallContext internalCallContext = null;

        if (plugin.isRetryAborted(stateContext.getDirectPaymentTransactionExternalKey())) {
            return OperationResult.EXCEPTION;
        }

        try {
            directPaymentProcessor.createAuthorization(stateContext.account, stateContext.directPaymentId, stateContext.getAmount(), stateContext.getCurrency(), stateContext.directPaymentExternalKey, stateContext.getProperties(), stateContext.callContext, stateContext.internalCallContext);
        } catch (PaymentApiException e) {

            final DateTime nextRetryDate = plugin.getNextRetryDate(stateContext.getDirectPaymentTransactionExternalKey());
            if (nextRetryDate == null) {
                // Very hacky, we are using EXCEPTION result to transition to final ABORTED state.
                throw new OperationException(e, OperationResult.EXCEPTION);
            } else {

                throw new OperationException(e, OperationResult.FAILURE);
            }
        }
        return OperationResult.SUCCESS;
    }
}
