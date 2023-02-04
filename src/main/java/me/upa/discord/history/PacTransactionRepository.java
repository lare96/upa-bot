package me.upa.discord.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PacTransactionRepository implements Serializable {
    private static final long serialVersionUID = -3889831741859538246L;

    private final ListMultimap<Long, PacTransaction> transactions = ArrayListMultimap.create();

    public void store(PacTransaction transaction) {
        transactions.put(transaction.getMemberId(), transaction);
    }

    public List<PacTransaction> getAll(long memberId) {
        return transactions.get(memberId);
    }

    public byte[] getArchive(long memberId) {
        List<PacTransaction> transactionList = new ArrayList<>(getAll(memberId));
        transactionList.sort((o1, o2) -> o2.getTimestamp().compareTo(o1.getTimestamp()));
        CsvMapper csvMapper = new CsvMapper();
        List<Object[]> sheetTransactionList = new ArrayList<>();
        sheetTransactionList.add(new Object[]{"Amount", "Reason", "Timestamp"});
        for (PacTransaction transaction : transactionList)
            sheetTransactionList.add(new Object[]{transaction.getAmount(), transaction.getReason(), transaction.getTimestamp().toString()});
        try {
            return csvMapper.writeValueAsBytes(sheetTransactionList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
