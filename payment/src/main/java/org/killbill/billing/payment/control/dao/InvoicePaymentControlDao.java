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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.PreparedBatch;
import org.skife.jdbi.v2.PreparedBatchPart;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;

import com.google.common.base.Objects;

public class InvoicePaymentControlDao {

    private final IDBI dbi;

    public InvoicePaymentControlDao(final IDBI dbi) {
        this.dbi = dbi;
    }

    public void insertAutoPayOff(final PluginAutoPayOffModelDao data) {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final String paymentId = Objects.firstNonNull(data.getPaymentId(), "").toString();
                final String paymentMethodId = Objects.firstNonNull(data.getPaymentMethodId(), "").toString();
                handle.execute("insert into _invoice_payment_control_plugin_auto_pay_off " +
                               "(payment_external_key, transaction_external_key, account_id, plugin_name, payment_id, payment_method_id, amount, currency, created_by, created_date) values " +
                               "(?,?,?,?,?,?,?,?,?,?)",
                               data.getPaymentExternalKey(), data.getTransactionExternalKey(), data.getAccountId(), data.getPluginName(), paymentId, paymentMethodId,
                               data.getAmount(), data.getCurrency(), data.getCreatedBy(), data.getCreatedDate()
                              );
                return null;
            }
        });
    }

    public List<PluginAutoPayOffModelDao> getAutoPayOffEntry(final UUID accountId) {
        return dbi.withHandle(new HandleCallback<List<PluginAutoPayOffModelDao>>() {
            @Override
            public List<PluginAutoPayOffModelDao> withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> queryResult = handle.select("select * from _invoice_payment_control_plugin_auto_pay_off where account_id = ?", accountId.toString());
                final List<PluginAutoPayOffModelDao> result = new ArrayList<PluginAutoPayOffModelDao>(queryResult.size());
                for (final Map<String, Object> row : queryResult) {

                    final PluginAutoPayOffModelDao entry = new PluginAutoPayOffModelDao((Long) row.get("record_id"),
                                                                                        (String) row.get("payment_external_key"),
                                                                                        (String) row.get("transaction_external_key"),
                                                                                        UUID.fromString((String) row.get("account_id")),
                                                                                        (String) row.get("plugin_name"),
                                                                                        UUID.fromString((String) row.get("payment_id")),
                                                                                        UUID.fromString((String) row.get("payment_method_id")),
                                                                                        (BigDecimal) row.get("amount"),
                                                                                        Currency.valueOf((String) row.get("currency")),
                                                                                        (String) row.get("created_by"),
                                                                                        getDateTime(row.get("created_date")));
                    result.add(entry);

                }
                return result;
            }
        });
    }

    public void insertPluginProperties(final List<PluginPropertyModelDao> dataEntries) {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {

                final PreparedBatch batch = handle.prepareBatch("insert into _invoice_payment_control_plugin_properties " +
                                                                "(payment_external_key, transaction_external_key, account_id, plugin_name, prop_key, prop_value, created_by, created_date) values " +
                                                                "(?,?,?,?,?,?,?,?)");
                for (PluginPropertyModelDao data : dataEntries) {
                    final PreparedBatchPart entry = batch.add();
                    entry.bind(0, data.getPaymentExternalKey())
                         .bind(1, data.getTransactionExternalKey())
                         .bind(2, data.getAccountId().toString())
                         .bind(3, data.getPluginName())
                         .bind(4, data.getPropKey())
                         .bind(5, data.getPropValue())
                         .bind(6, data.getCreatedBy())
                         .bind(7, data.getCreatedDate());
                }
                batch.execute();
                return null;
            }
        });
    }

    public List<PluginPropertyModelDao> getPluginProperties(final String transactionExternalkey) {

        return dbi.withHandle(new HandleCallback<List<PluginPropertyModelDao>>() {
            @Override
            public List<PluginPropertyModelDao> withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> queryResult = handle.select("select * from _invoice_payment_control_plugin_properties where transaction_external_key = ?", transactionExternalkey);
                final List<PluginPropertyModelDao> result = new ArrayList<PluginPropertyModelDao>(queryResult.size());
                for (final Map<String, Object> row : queryResult) {
                    final PluginPropertyModelDao entry = new PluginPropertyModelDao((Long) row.get("record_id"),
                                                                                    (String) row.get("payment_external_key"),
                                                                                    (String) row.get("transaction_external_key"),
                                                                                    UUID.fromString((String) row.get("account_id")),
                                                                                    (String) row.get("plugin_name"),
                                                                                    (String) row.get("prop_key"),
                                                                                    (String) row.get("prop_value"),
                                                                                    (String) row.get("created_by"),
                                                                                    getDateTime(row.get("created_date")));
                    result.add(entry);
                }
                return result;
            }
        });
    }

    protected DateTime getDateTime(final Object timestamp) throws SQLException {
        final Timestamp resultStamp = (Timestamp) timestamp;
        return new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
    }
}
