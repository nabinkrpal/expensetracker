package com.example.expenseapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.expenseapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deletedTransaction: Transaction
    private var transactions: List<Transaction> = emptyList()
    private var oldTransactions: List<Transaction> = emptyList()
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Database instance (could be moved to a singleton in App class)
        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "transactions"
        ).build()

        // RecyclerView setup
        transactionAdapter = TransactionAdapter(transactions)
        binding.recyclerview.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Swipe to delete
        val itemTouchHelper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                deleteTransaction(transactions[viewHolder.adapterPosition])
            }
        }
        ItemTouchHelper(itemTouchHelper).attachToRecyclerView(binding.recyclerview)

        // Add button click
        binding.addBtn.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    // Fetch all transactions
    private fun fetchAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            transactions = db.transactionDao().getAll()

            withContext(Dispatchers.Main) {
                updateDashboard()
                transactionAdapter.setData(transactions)
            }
        }
    }

    // Update dashboard values
    private fun updateDashboard() {
        val totalAmount = transactions.sumOf { it.amount }
        val budgetAmount = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val expenseAmount = totalAmount - budgetAmount

        binding.balance.text = "₹ %.2f".format(totalAmount)
        binding.budget.text = "₹ %.2f".format(budgetAmount)
        binding.expense.text = "₹ %.2f".format(expenseAmount)
    }

    // Undo delete
    private fun undoDelete() {
        lifecycleScope.launch(Dispatchers.IO) {
            db.transactionDao().insertAll(deletedTransaction)
            transactions = oldTransactions

            withContext(Dispatchers.Main) {
                transactionAdapter.setData(transactions)
                updateDashboard()
            }
        }
    }

    // Snackbar for undo
    private fun showSnackbar() {
        val view = binding.coordinator
        val snackbar = Snackbar.make(view, "Transaction deleted!", Snackbar.LENGTH_LONG)
        snackbar.setAction("Undo") { undoDelete() }
            .setActionTextColor(ContextCompat.getColor(this, R.color.red))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    // Delete a transaction
    private fun deleteTransaction(transaction: Transaction) {
        deletedTransaction = transaction
        oldTransactions = transactions

        lifecycleScope.launch(Dispatchers.IO) {
            db.transactionDao().delete(transaction)
            transactions = transactions.filter { it.id != transaction.id }

            withContext(Dispatchers.Main) {
                updateDashboard()
                transactionAdapter.setData(transactions)
                showSnackbar()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchAll()
    }
}


//

package com.example.expenseapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;

import androidx.room.Room;

import com.example.expenseapp.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private List<Transaction> transactions, oldTransactions;
    private Transaction deletedTransaction;
    private TransactionAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = Room.databaseBuilder(this, AppDatabase.class, "transactions").build();

        adapter = new TransactionAdapter(transactions);
        b.recyclerview.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerview.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) { return false; }
            public void onSwiped(RecyclerView.ViewHolder vh, int d) {
                deleteTransaction(transactions.get(vh.getAdapterPosition()));
            }
        }).attachToRecyclerView(b.recyclerview);

        b.addBtn.setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
    }

    private void fetchAll() {
        Executors.newSingleThreadExecutor().execute(() -> {
            transactions = db.transactionDao().getAll();
            runOnUiThread(() -> { updateDashboard(); adapter.setData(transactions); });
        });
    }

    private void updateDashboard() {
        double total = 0, budget = 0;
        for (Transaction t : transactions) {
            total += t.amount;
            if (t.amount > 0) budget += t.amount;
        }
        b.balance.setText("₹ " + String.format("%.2f", total));
        b.budget.setText("₹ " + String.format("%.2f", budget));
        b.expense.setText("₹ " + String.format("%.2f", total - budget));
    }

    private void undoDelete() {
        Executors.newSingleThreadExecutor().execute(() -> {
            db.transactionDao().insertAll(deletedTransaction);
            transactions = oldTransactions;
            runOnUiThread(() -> { adapter.setData(transactions); updateDashboard(); });
        });
    }

    private void showSnackbar() {
        Snackbar.make(b.coordinator, "Transaction deleted!", Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> undoDelete())
                .setActionTextColor(ContextCompat.getColor(this, R.color.red))
                .setTextColor(ContextCompat.getColor(this, R.color.white))
                .show();
    }

    private void deleteTransaction(Transaction t) {
        deletedTransaction = t;
        oldTransactions = transactions;

        Executors.newSingleThreadExecutor().execute(() -> {
            db.transactionDao().delete(t);
            transactions = transactions.stream().filter(x -> x.id != t.id).toList();

            runOnUiThread(() -> {
                updateDashboard();
                adapter.setData(transactions);
                showSnackbar();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchAll();
    }
}
