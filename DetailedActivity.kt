package com.example.expenseapp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.expenseapp.databinding.ActivityDetailedBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailedBinding
    private lateinit var transaction: Transaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve transaction object
        transaction = intent.getSerializableExtra("transaction") as Transaction

        // Set existing data
        binding.labelInput.setText(transaction.label)
        binding.amountInput.setText(transaction.amount.toString())
        binding.descriptionInput.setText(transaction.description)

        // Hide keyboard when clicking outside
        binding.rootView.setOnClickListener {
            currentFocus?.let { view ->
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }

        // Handle text changes
        binding.labelInput.addTextChangedListener {
            binding.updateBtn.visibility = View.VISIBLE
            if (!it.isNullOrEmpty()) binding.labelLayout.error = null
        }

        binding.amountInput.addTextChangedListener {
            binding.updateBtn.visibility = View.VISIBLE
            if (!it.isNullOrEmpty()) binding.amountLayout.error = null
        }

        binding.descriptionInput.addTextChangedListener {
            binding.updateBtn.visibility = View.VISIBLE
        }

        // Update button click
        binding.updateBtn.setOnClickListener {
            val label = binding.labelInput.text.toString()
            val description = binding.descriptionInput.text.toString()
            val amount = binding.amountInput.text.toString().toDoubleOrNull()

            if (label.isEmpty()) {
                binding.labelLayout.error = "Please enter a valid label"
            } else if (amount == null) {
                binding.amountLayout.error = "Please enter a valid amount"
            } else {
                val updatedTransaction = Transaction(transaction.id, label, amount, description)
                update(updatedTransaction)
            }
        }

        // Close button click
        binding.closeBtn.setOnClickListener {
            finish()
        }
    }

    private fun update(transaction: Transaction) {
        val db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "transactions"
        ).build()

        lifecycleScope.launch(Dispatchers.IO) {
            db.transactionDao().update(transaction)

            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}
