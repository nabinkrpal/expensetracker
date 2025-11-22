package com.example.expenseapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.expenseapp.databinding.ActivityAddTransactionBinding

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clear errors on text change
        binding.labelInput.addTextChangedListener {
            if (!it.isNullOrEmpty()) binding.labelLayout.error = null
        }

        binding.amountInput.addTextChangedListener {
            if (!it.isNullOrEmpty()) binding.amountLayout.error = null
        }

        // Add transaction button
        binding.addTransactionBtn.setOnClickListener {
            val label = binding.labelInput.text.toString()
            val description = binding.descriptionInput.text.toString()
            val amount = binding.amountInput.text.toString().toDoubleOrNull()

            if (label.isEmpty()) {
                binding.labelLayout.error = "Please enter a valid label"
            } else if (amount == null) {
                binding.amountLayout.error = "Please enter a valid amount"
            } else {
                val transaction = Transaction(0, label, amount, description)
                insert(transaction)
            }
        }

        // Close button
        binding.closeBtn.setOnClickListener {
            finish()
        }
    }

    private fun insert(transaction: Transaction) {
        val db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "transactions"
        ).build()

        lifecycleScope.launch(Dispatchers.IO) {
            db.transactionDao().insertAll(transaction)

            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}
