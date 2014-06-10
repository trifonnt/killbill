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

package org.killbill.billing.payment.control.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestInvoicePaymentControlDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testPluginAutoPayOffSimple() {
        InvoicePaymentControlDao dao = new InvoicePaymentControlDao(dbi);

        UUID accountId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("13.33");
        DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao("key1", "tkey1", accountId, "XXX", paymentId, methodId, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(accountId);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).getPaymentExternalKey(), "key1");
        assertEquals(entries.get(0).getTransactionExternalKey(), "tkey1");
        assertEquals(entries.get(0).getAccountId(), accountId);
        assertEquals(entries.get(0).getPluginName(), "XXX");
        assertEquals(entries.get(0).getPaymentId(), paymentId);
        assertEquals(entries.get(0).getPaymentMethodId(), methodId);
        assertEquals(entries.get(0).getAmount().compareTo(amount), 0);
        assertEquals(entries.get(0).getCurrency(), Currency.USD);
        assertEquals(entries.get(0).getCreatedBy(), "lulu");
        assertEquals(entries.get(0).getCreatedDate(), utcNow);
    }

    @Test(groups = "slow")
    public void testPluginAutoPayOffMutlitpleEntries() {
        InvoicePaymentControlDao dao = new InvoicePaymentControlDao(dbi);

        UUID accountId = UUID.randomUUID();
        UUID paymentId1 = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("13.33");
        DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao("key1", "tkey1", accountId, "XXX", paymentId1, methodId, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        UUID paymentId2 = UUID.randomUUID();
        final PluginAutoPayOffModelDao entry2 = new PluginAutoPayOffModelDao("key2", "tkey2", accountId, "XXX", paymentId2, methodId, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry2);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(accountId);
        assertEquals(entries.size(), 2);
    }

    @Test(groups = "slow")
    public void testPluginAutoPayOffNoEntries() {
        InvoicePaymentControlDao dao = new InvoicePaymentControlDao(dbi);

        UUID accountId = UUID.randomUUID();
        UUID paymentId1 = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("13.33");
        DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao("key1", "tkey1", accountId, "XXX", paymentId1, methodId, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(UUID.randomUUID());
        assertEquals(entries.size(), 0);
    }

    @Test(groups = "slow")
    public void testInsertPluginPropertiesSimple() {
        InvoicePaymentControlDao dao = new InvoicePaymentControlDao(dbi);

        UUID accountId = UUID.randomUUID();
        DateTime utcNow = clock.getUTCNow();
        PluginPropertyModelDao entry = new PluginPropertyModelDao("key1", "tkey1", accountId, "XXX", "prop_key", "prop_value", "lulu", utcNow);
        dao.insertPluginProperties(Collections.singletonList(entry));

        List<PluginPropertyModelDao> entries = dao.getPluginProperties("tkey1");
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).getPaymentExternalKey(), "key1");
        assertEquals(entries.get(0).getTransactionExternalKey(), "tkey1");
        assertEquals(entries.get(0).getAccountId(), accountId);
        assertEquals(entries.get(0).getPluginName(), "XXX");
        assertEquals(entries.get(0).getPropKey(), "prop_key");
        assertEquals(entries.get(0).getPropValue(), "prop_value");
        assertEquals(entries.get(0).getCreatedBy(), "lulu");
        assertEquals(entries.get(0).getCreatedDate(), utcNow);
    }

    @Test(groups = "slow")
    public void testInsertPluginPropertiesMultipleEntries() {
        InvoicePaymentControlDao dao = new InvoicePaymentControlDao(dbi);

        UUID accountId = UUID.randomUUID();
        DateTime utcNow = clock.getUTCNow();
        PluginPropertyModelDao entry1 = new PluginPropertyModelDao("key", "tkey", accountId, "XXX", "prop_key1", "prop_value1", "lulu", utcNow);
        PluginPropertyModelDao entry2 = new PluginPropertyModelDao("key", "tkey", accountId, "XXX", "prop_key2", "prop_value2", "lulu", utcNow);
        PluginPropertyModelDao entry3 = new PluginPropertyModelDao("key", "tkey", accountId, "XXX", "prop_key3", "prop_value3", "lulu", utcNow);
        List input = new ArrayList();
        input.add(entry1);
        input.add(entry2);
        input.add(entry3);
        dao.insertPluginProperties(input);

        List<PluginPropertyModelDao> entries = dao.getPluginProperties("tkey");
        assertEquals(entries.size(), 3);
    }

}
