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
