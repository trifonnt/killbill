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

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;

public class RetryEnteringStateCallback implements EnteringStateCallback {

    private RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final DirectPaymentStateContext directPaymentStateContext;

    public RetryEnteringStateCallback(final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner, final DirectPaymentStateContext directPaymentStateContext) {
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
        this.directPaymentStateContext = directPaymentStateContext;
    }

    @Override
    public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        // STEPH Can we do pass it through some state machine context.
        final PaymentAttemptModelDao attempt = retryableDirectPaymentAutomatonRunner.paymentDao.getPaymentAttemptByExternalKey(directPaymentStateContext.getDirectPaymentTransactionExternalKey(), directPaymentStateContext.internalCallContext);
        retryableDirectPaymentAutomatonRunner.paymentDao.updatePaymentAttempt(attempt.getId(), state.getName(), directPaymentStateContext.internalCallContext);

        // If RETRIED state add notificationDate
    }
}
