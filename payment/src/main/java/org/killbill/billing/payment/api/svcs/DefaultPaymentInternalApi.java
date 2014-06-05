/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentInternalApi;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.sm.RetryableDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.retry.InvoiceRetryPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableMap;

public class DefaultPaymentInternalApi implements PaymentInternalApi {

    private final PaymentMethodProcessor methodProcessor;
    private final DirectPaymentProcessor directPaymentProcessor;

    private final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final NonEntityDao nonEntityDao;

    @Inject
    public DefaultPaymentInternalApi(final DirectPaymentProcessor directPaymentProcessor, final PaymentMethodProcessor methodProcessor, final NonEntityDao nonEntityDao, final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner) {
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
        this.methodProcessor = methodProcessor;
        this.nonEntityDao = nonEntityDao;
        this.directPaymentProcessor = directPaymentProcessor;
    }

    @Override
    public DirectPayment createPayment(final Account account, final UUID invoiceId,
                                       @Nullable final BigDecimal amount, final Iterable<PluginProperty> properties, final InternalCallContext internalContext) throws PaymentApiException {

        final CallContext callContext = internalContext.toCallContext(nonEntityDao.retrieveIdFromObject(internalContext.getTenantRecordId(), ObjectType.TENANT));
        return retryableDirectPaymentAutomatonRunner.run(true,
                                                         TransactionType.PURCHASE,
                                                         account,
                                                         account.getPaymentMethodId(),
                                                         null,
                                                         invoiceId.toString(),
                                                         UUID.randomUUID().toString(),
                                                         amount,
                                                         account.getCurrency(),
                                                         false,
                                                         ImmutableMap.<UUID, BigDecimal>of(),
                                                         properties,
                                                         InvoiceRetryPluginApi.PLUGIN_NAME,
                                                         callContext,
                                                         internalContext);
    }

    @Override
    public DirectPayment getPayment(final UUID paymentId, final Iterable<PluginProperty> properties, final InternalTenantContext internalTenantContext) throws PaymentApiException {

        final TenantContext tenantContext = internalTenantContext.toTenantContext(nonEntityDao.retrieveIdFromObject(internalTenantContext.getTenantRecordId(), ObjectType.TENANT));
        final DirectPayment payment = directPaymentProcessor.getPayment(paymentId, false, properties, tenantContext, internalTenantContext);
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedInactive, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedInactive, false, properties, context);
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final InternalTenantContext context) throws PaymentApiException {
        return directPaymentProcessor.getAccountPayments(accountId, context);
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, false, properties, context);
    }
}
