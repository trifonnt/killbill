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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.DefaultStateMachineConfig;
import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.xmlloader.XMLLoader;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.io.Resources;

public class RetryableDirectPaymentAutomatonRunner extends DirectPaymentAutomatonRunner {

    private final TagInternalApi tagApi;

    private final StateMachine retryStateMachine;

    private final DirectPaymentProcessor directPaymentProcessor;
    private final State initialState;
    private final Operation retryOperation;

    public RetryableDirectPaymentAutomatonRunner(final StateMachineConfig stateMachineConfig, final PaymentDao paymentDao, final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry, final Clock clock, final TagInternalApi tagApi, final DirectPaymentProcessor directPaymentProcessor) {
        super(stateMachineConfig, paymentDao, locker, paymentPluginDispatcher, pluginRegistry, clock);
        this.tagApi = tagApi;
        this.directPaymentProcessor = directPaymentProcessor;
        this.retryStateMachine = fetchRetryStateMachine();
        this.initialState = fetchInitialState();
        this.retryOperation = fetchRetryOperation();
    }

    public UUID run(final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                    @Nullable final UUID directPaymentId, @Nullable final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                    @Nullable final BigDecimal amount, @Nullable final Currency currency,
                    final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        // STEPH Actually logic should be passed below the retryable layer as well...
        // STEPH check AUTO_PAY_OFF prior to entering state machine
        if (isAccountAutoPayOff(account.getId(), internalCallContext)) {
            // Very hacky, we are using FAILURE so that removing AUTO_PAY_OFF retries the attempt.
            //return OperationResult.FAILURE;
            return null;
        }

        final DateTime utcNow = clock.getUTCNow();

        final DirectPaymentStateContext directPaymentStateContext = new DirectPaymentStateContext(directPaymentId, directPaymentExternalKey, directPaymentTransactionExternalKey, transactionType, account, paymentMethodId, amount, currency, properties, internalCallContext, callContext);

        // TOTO should be passed at the API level.
        final String transactionExternalKey = null;

        try {

            final OperationCallback callback;

            switch (transactionType) {
                case PURCHASE:
                    callback = null;
                    break;
                case AUTHORIZE:
                    callback = new RetryAuthorizeOperationCallback(directPaymentStateContext, directPaymentProcessor);
                    break;
                case CAPTURE:
                    callback = null;
                    break;
                case VOID:
                    callback = null;
                    break;
                case CREDIT:
                    callback = null;
                    break;
                default:
                    throw new IllegalStateException("Unsupported transaction type " + transactionType);
            }

            final LeavingStateCallback leavingStateCallback = new RetryLeavingStateCallback(directPaymentStateContext, initialState, transactionType);
            final EnteringStateCallback enteringStateCallback = new RetryEnteringStateCallback(directPaymentStateContext);

            initialState.runOperation(retryOperation, callback, enteringStateCallback, leavingStateCallback);
        } catch (MissingEntryException e) {
            // STEPH_RETRY
            throw new PaymentApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        } catch (OperationException e) {
            // STEPH_RETRY
            throw new PaymentApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        }

        return directPaymentStateContext.getDirectPaymentId();
    }

    private final static StateMachine fetchRetryStateMachine() {
        try {
            DefaultStateMachineConfig retryStateMachineConfig = XMLLoader.getObjectFromString(Resources.getResource("RetryStates.xml").toExternalForm(), DefaultStateMachineConfig.class);
            return retryStateMachineConfig.getStateMachine("PAYMENT_RETRY");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final State fetchInitialState() {
        try {
            return retryStateMachine.getState("INIT");
        } catch (MissingEntryException e) {
            throw new RuntimeException(e);
        }
    }

    private final Operation fetchRetryOperation() {
        try {
            return retryStateMachine.getOperation("OP_RETRY");
        } catch (MissingEntryException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isAccountAutoPayOff(final UUID accountId, final InternalTenantContext context) {
        final List<Tag> accountTags = tagApi.getTags(accountId, ObjectType.ACCOUNT, context);

        return ControlTagType.isAutoPayOff(Collections2.transform(accountTags, new Function<Tag, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }

    public class RetryLeavingStateCallback implements LeavingStateCallback {

        private final DirectPaymentStateContext stateContext;
        private final State initialState;
        private final TransactionType transactionType;

        public RetryLeavingStateCallback(final DirectPaymentStateContext stateContext, final State initialState, final TransactionType transactionType) {
            this.initialState = initialState;
            this.stateContext = stateContext;
            this.transactionType = transactionType;
        }

        @Override
        public void leavingState(final State state) {

            final DateTime utcNow = clock.getUTCNow();

            if (state.getName().equals(initialState.getName())) {
                // STEPH_RETRY transactionId does not exist yet, and how do we get pluginName ?
                paymentDao.insertPaymentAttempt(new PaymentAttemptModelDao(utcNow, utcNow, null, stateContext.directPaymentTransactionExternalKey, state.getName(), transactionType.name(), null), stateContext.internalCallContext);
            }
        }
    }

    public abstract class RetryOperationCallback implements OperationCallback {

        protected final DirectPaymentProcessor directPaymentProcessor;

        public RetryOperationCallback(final DirectPaymentProcessor directPaymentProcessor) {
            this.directPaymentProcessor = directPaymentProcessor;
        }
    }

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

    public class RetryEnteringStateCallback implements EnteringStateCallback {

        private final DirectPaymentStateContext directPaymentStateContext;

        public RetryEnteringStateCallback(final DirectPaymentStateContext directPaymentStateContext) {
            this.directPaymentStateContext = directPaymentStateContext;
        }

        @Override
        public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
            // STEPH Can we do pass it through some state machine context.
            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttemptByExternalKey(directPaymentStateContext.getDirectPaymentTransactionExternalKey(), directPaymentStateContext.internalCallContext);
            paymentDao.updatePaymentAttempt(attempt.getId(), state.getName(), directPaymentStateContext.internalCallContext);

            // If RETRIED state add notificationDate
        }
    }
}
