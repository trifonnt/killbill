/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TestPaymentHelper {

    protected final AccountUserApi accountApi;
    protected final AccountInternalApi accountInternalApi;
    protected final InvoiceInternalApi invoiceApi;
    protected PaymentApi paymentApi;
    private final PersistentBus eventBus;
    private final Clock clock;
    private final MockNonEntityDao mockNonEntityDao;
    private final InternalCallContext internalCallContext;
    private final CallContext context;

    @Inject
    public TestPaymentHelper(final AccountUserApi accountApi,
                             final AccountInternalApi accountInternalApi,
                             final InvoiceInternalApi invoiceApi,
                             final PaymentApi paymentApi,
                             final PersistentBus eventBus,
                             final Clock clock,
                             final MockNonEntityDao mockNonEntityDao,
                             final InternalCallContext internalCallContext,
                             final CallContext context) {
        this.accountApi = accountApi;
        this.eventBus = eventBus;
        this.accountInternalApi = accountInternalApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.mockNonEntityDao = mockNonEntityDao;
        this.internalCallContext = internalCallContext;
        this.context = context;
    }

    public Invoice createTestInvoice(final Account account,
                                     final LocalDate targetDate,
                                     final Currency currency,
                                     final InvoiceItem... items) throws EventBusException, InvoiceApiException {
        final Invoice invoice = new MockInvoice(account.getId(), clock.getUTCToday(), targetDate, currency);

        for (final InvoiceItem item : items) {
            if (item instanceof MockRecurringInvoiceItem) {
                final MockRecurringInvoiceItem recurringInvoiceItem = (MockRecurringInvoiceItem) item;
                invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                                    account.getId(),
                                                                    recurringInvoiceItem.getBundleId(),
                                                                    recurringInvoiceItem.getSubscriptionId(),
                                                                    recurringInvoiceItem.getPlanName(),
                                                                    recurringInvoiceItem.getPhaseName(),
                                                                    null,
                                                                    recurringInvoiceItem.getStartDate(),
                                                                    recurringInvoiceItem.getEndDate(),
                                                                    recurringInvoiceItem.getAmount(),
                                                                    recurringInvoiceItem.getRate(),
                                                                    recurringInvoiceItem.getCurrency()));
            }
        }

        Mockito.when(invoiceApi.getInvoiceById(Mockito.eq(invoice.getId()), Mockito.<InternalTenantContext>any())).thenReturn(invoice);
        Mockito.when(invoiceApi.getInvoiceForPaymentId(Mockito.<UUID>any(), Mockito.<InternalCallContext>any())).thenReturn(invoice);

        final InvoiceCreationInternalEvent event = new MockInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                                invoice.getBalance(), invoice.getCurrency(),
                                                                                invoice.getInvoiceDate(), 1L, 2L, null);

        eventBus.post(event);
        return invoice;
    }

    public Account createTestAccount(final String email, final boolean addPaymentMethod) throws Exception {
        final String name = "First" + UUID.randomUUID().toString() + " " + "Last" + UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();

        final Account accountData = Mockito.mock(Account.class);
        Mockito.when(accountData.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(accountData.getExternalKey()).thenReturn(externalKey);
        Mockito.when(accountData.getName()).thenReturn(name);
        Mockito.when(accountData.getFirstNameLength()).thenReturn(10);
        Mockito.when(accountData.getPhone()).thenReturn("123-456-7890");
        Mockito.when(accountData.getEmail()).thenReturn(email);
        Mockito.when(accountData.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(accountData.getBillCycleDayLocal()).thenReturn(1);
        Mockito.when(accountData.isMigrated()).thenReturn(false);
        Mockito.when(accountData.isNotifiedForInvoices()).thenReturn(false);

        Account account;
        if (isFastTest()) {
            account = accountData;
            Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
            Mockito.when(accountInternalApi.getAccountByKey(Mockito.anyString(), Mockito.<InternalTenantContext>any())).thenReturn(account);
            mockNonEntityDao.addTenantRecordIdMapping(account.getId(), internalCallContext);
        } else {
            account = accountApi.createAccount(accountData, context);
        }

        if (addPaymentMethod) {
            final PaymentMethodPlugin pm = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
            account = addTestPaymentMethod(account, pm);
        }

        return account;
    }

    public Account addTestPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo) throws Exception {
        final UUID paymentMethodId = paymentApi.addPaymentMethod(account, paymentMethodInfo.getExternalPaymentMethodId(), MockPaymentProviderPlugin.PLUGIN_NAME, true, paymentMethodInfo, ImmutableList.<PluginProperty>of(), context);
        if (isFastTest()) {
            Mockito.when(account.getPaymentMethodId()).thenReturn(paymentMethodId);
            return account;
        } else {
            // To reflect the payment method id change
            return accountApi.getAccountById(account.getId(), context);
        }
    }

    // Unfortunately, this helper is shared across fast and slow tests
    private boolean isFastTest() {
        return Mockito.mockingDetails(accountInternalApi).isMock();
    }
}
