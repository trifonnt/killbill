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
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;

public class RetryLeavingStateCallback implements LeavingStateCallback {

    private PluginControlledDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final DirectPaymentStateContext stateContext;
    private final State initialState;
    private final TransactionType transactionType;

    public RetryLeavingStateCallback(final PluginControlledDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner, final DirectPaymentStateContext stateContext, final State initialState, final TransactionType transactionType) {
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
        this.initialState = initialState;
        this.stateContext = stateContext;
        this.transactionType = transactionType;
    }

    @Override
    public void leavingState(final State state) {

        final DateTime utcNow = retryableDirectPaymentAutomatonRunner.clock.getUTCNow();
        if (state.getName().equals(initialState.getName())) {
            retryableDirectPaymentAutomatonRunner.paymentDao.insertPaymentAttempt(new PaymentAttemptModelDao(utcNow, utcNow, null, stateContext.directPaymentTransactionExternalKey, state.getName(), transactionType.name(), null), stateContext.internalCallContext);
        }
    }
}
