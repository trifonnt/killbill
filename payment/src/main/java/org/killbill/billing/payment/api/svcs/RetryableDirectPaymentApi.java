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

package org.killbill.billing.payment.api.svcs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

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
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.XMLLoader;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.io.Resources;

public class RetryableDirectPaymentApi implements DirectPaymentApi {

    protected final OSGIServiceRegistration<RetryPluginApi> pluginRegistry;

    private final DirectPaymentApi defaultDirectPaymentApi;
    private final StateMachine retryStateMachine;
    private final Operation retryOperation;
    private final PaymentDao paymentDao;
    private final State initialState;
    private final TagInternalApi tagInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    private final Clock clock;

    @Inject
    public RetryableDirectPaymentApi(final OSGIServiceRegistration<RetryPluginApi> pluginRegistry,
                                     final DirectPaymentApi defaultDirectPaymentApi,
                                     final PaymentDao paymentDao,
                                     final TagInternalApi tagInternalApi,
                                     final Clock clock,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.pluginRegistry = pluginRegistry;
        this.defaultDirectPaymentApi = defaultDirectPaymentApi;
        this.paymentDao = paymentDao;
        this.tagInternalApi = tagInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.retryStateMachine = fetchRetryStateMachine();
        this.retryOperation = fetchOperation();
        this.initialState = fetchInitialState();
    }

    protected RetryPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final RetryPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            // STEPH error code
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
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

    private final Operation fetchOperation() {
        try {
            return retryStateMachine.getOperation("OP_RETRY");
        } catch (MissingEntryException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isAccountAutoPayOff(final UUID accountId, final InternalTenantContext context) {
        final List<Tag> accountTags = tagInternalApi.getTags(accountId, ObjectType.ACCOUNT, context);

        return ControlTagType.isAutoPayOff(Collections2.transform(accountTags, new Function<Tag, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }

    public class RetryLeavingStateCallback implements LeavingStateCallback {

        private final PaymentDao paymentDao;
        private final State initialState;
        private final UUID accountId;
        private final String transactionExternalKey;
        private final Clock clock;
        private final Operation operation;
        private final InternalCallContext context;

        public RetryLeavingStateCallback(final UUID accountId, final PaymentDao paymentDao, final State initialState, final String externalKey, final Clock clock, final Operation operation, final InternalCallContext context) {
            this.accountId = accountId;
            this.paymentDao = paymentDao;
            this.initialState = initialState;
            this.transactionExternalKey = externalKey;
            this.clock = clock;
            this.operation = operation;
            this.context = context;
        }

        @Override
        public void leavingState(final State state) {

            final DateTime utcNow = clock.getUTCNow();

            if (state.getName().equals(initialState.getName())) {
                // STEPH_RETRY transactionId does not exist yet, and how do we get pluginName ?
                paymentDao.insertPaymentAttempt(new PaymentAttemptModelDao(utcNow, utcNow, null, transactionExternalKey, state.getName(), operation.getName(), null), context);
            }


        }
    }

    public abstract class RetryOperationCallback implements OperationCallback {

        protected final DirectPaymentApi delegate;

        public RetryOperationCallback(final DirectPaymentApi delegate) {
            this.delegate = delegate;
        }
    }

    public class RetryAuthorizeOperationCallback extends RetryOperationCallback {

        private final Account account;
        private final UUID directPaymentId;
        private final BigDecimal amount;
        private final Currency currency;
        private final String transactionExternalKey;
        private final Iterable<PluginProperty> properties;
        private final CallContext context;

        public RetryAuthorizeOperationCallback(final DirectPaymentApi delegate, final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String externalKey, final Iterable<PluginProperty> properties, final CallContext context) {
            super(delegate);
            this.account = account;
            this.directPaymentId = directPaymentId;
            this.amount = amount;
            this.currency = currency;
            this.transactionExternalKey = externalKey;
            this.properties = properties;
            this.context = context;
        }

        @Override
        public OperationResult doOperationCallback() throws OperationException {

            // STEPH retrieve plugin ?
            RetryPluginApi plugin = null;

            InternalCallContext internalCallContext = null;
            // STEPH check AUTO_PAY_OFF prior to entering state machine
            if (isAccountAutoPayOff(account.getId(), internalCallContext)) {
                // Very hacky, we are using FAILURE so that removing AUTO_PAY_OFF retries the attempt.
                return OperationResult.FAILURE;

            }


            if (plugin.isRetryAborted(transactionExternalKey)) {
                return OperationResult.EXCEPTION;
            }

            try {
                delegate.createAuthorization(account, directPaymentId, amount, currency, transactionExternalKey, properties, context);
            } catch (PaymentApiException e) {

                final DateTime nextRetryDate = plugin.getNextRetryDate(transactionExternalKey);
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

        private final PaymentDao paymentDao;
        private final UUID accountId;
        private final String externalKey;
        private final Clock clock;
        private final Operation operation;
        private final InternalCallContext context;

        public RetryEnteringStateCallback(final PaymentDao paymentDao, final UUID accountId, final String externalKey, final Clock clock, final Operation operation, final InternalCallContext context) {
            this.paymentDao = paymentDao;
            this.accountId = accountId;
            this.externalKey = externalKey;
            this.clock = clock;
            this.operation = operation;
            this.context = context;
        }

        @Override
        public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {

            // STEPH Can we do pass it through some state machine context.
            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttemptByExternalKey(externalKey, context);
            paymentDao.updatePaymentAttempt(attempt.getId(), state.getName(), context);

            // If RETRIED state add notificationDate

        }
    }

    @Override
    public DirectPayment createAuthorization(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String externalKey, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        // TOTO should be passed at the API level.
        final String transactionExternalKey = null;

        try {
            final OperationCallback callback = new RetryAuthorizeOperationCallback(defaultDirectPaymentApi, account, directPaymentId, amount, currency, externalKey, properties, context);

            final LeavingStateCallback leavingStateCallback = new RetryLeavingStateCallback(account.getId(), paymentDao, initialState, transactionExternalKey, clock, retryOperation, internalContext);
            final EnteringStateCallback enteringStateCallback = new RetryEnteringStateCallback(paymentDao, account.getId(), transactionExternalKey, clock, retryOperation, internalContext);

            initialState.runOperation(retryOperation, callback, enteringStateCallback, leavingStateCallback);
        } catch (MissingEntryException e) {
            // STEPH_RETRY
            throw new PaymentApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        } catch (OperationException e) {
            // STEPH_RETRY
            throw new PaymentApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        }
        return null;
    }

    @Override
    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public DirectPayment createPurchase(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String externalKey, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public DirectPayment createCredit(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final TenantContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return null;
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return null;
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return null;
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return null;
    }
}
