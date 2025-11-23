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
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.expenseapp.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Transaction deletedTransaction;
    private List<Transaction> transactions;
    private List<Transaction> oldTransactions;
    private TransactionAdapter transactionAdapter;
    private AppDatabase db;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Database
        db = Room.databaseBuilder(this, AppDatabase.class, "transactions").build();

        // RecyclerView setup
        transactionAdapter = new TransactionAdapter(transactions);
        binding.recyclerview.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerview.setAdapter(transactionAdapter);

        // Swipe delete
        ItemTouchHelper.SimpleCallback itemTouchHelper = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                deleteTransaction(transactions.get(vh.getAdapterPosition()));
            }
        };
        new ItemTouchHelper(itemTouchHelper).attachToRecyclerView(binding.recyclerview);

        // Add button
        binding.addBtn.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AddTransactionActivity.class)));
    }

    // Fetch all transactions
    private void fetchAll() {
        executor.execute(() -> {
            transactions = db.transactionDao().getAll();

            runOnUiThread(() -> {
                updateDashboard();
                transactionAdapter.setData(transactions);
            });
        });
    }

    // Update dashboard
    private void updateDashboard() {
        double totalAmount = 0;
        double budgetAmount = 0;

        for (Transaction t : transactions) {
            totalAmount += t.amount;
            if (t.amount > 0) budgetAmount += t.amount;
        }

        double expenseAmount = totalAmount - budgetAmount;

        binding.balance.setText("₹ " + String.format("%.2f", totalAmount));
        binding.budget.setText("₹ " + String.format("%.2f", budgetAmount));
        binding.expense.setText("₹ " + String.format("%.2f", expenseAmount));
    }

    // Undo delete
    private void undoDelete() {
        executor.execute(() -> {
            db.transactionDao().insertAll(deletedTransaction);
            transactions = oldTransactions;

            runOnUiThread(() -> {
                transactionAdapter.setData(transactions);
                updateDashboard();
            });
        });
    }

    // Snackbar
    private void showSnackbar() {
        Snackbar snackbar = Snackbar.make(binding.coordinator,
                "Transaction deleted!", Snackbar.LENGTH_LONG);

        snackbar.setAction("Undo", v -> undoDelete());
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.red));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.white));
        snackbar.show();
    }

    // Delete transaction
    private void deleteTransaction(Transaction transaction) {
        deletedTransaction = transaction;
        oldTransactions = transactions;

        executor.execute(() -> {
            db.transactionDao().delete(transaction);
            transactions = transactions.stream()
                    .filter(t -> t.id != transaction.id)
                    .toList();

            runOnUiThread(() -> {
                updateDashboard();
                transactionAdapter.setData(transactions);
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

