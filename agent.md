# Expense Tracker Mobile Application

## Project Overview

Build a modern mobile application called **Expense Tracker** for personal finance management.

The application is NOT connected to any bank account or payment gateway. Users manually input their income and expenses to track spending habits, manage budgets, and analyze financial behavior.

Target users are individuals who want a simple and intuitive way to monitor their personal finances.

The design should focus on:

* Modern UI/UX
* Light blue primary color theme
* Minimalist design
* Glassmorphism cards and components
* Clean typography
* Consistent spacing and component styling
* Smooth animations and transitions
* Mobile-first experience
* Support for both USD ($) and Cambodian Riel (៛)

---

# Authentication

## Login Screen

Features:

* Continue with Google
* Email + Password Login
* Remember Me option
* Forgot Password
* Modern illustration/header
* Glassmorphism login card

## Register Screen

Features:

* Username
* Email
* Password
* Confirm Password
* Continue with Google
* Validation messages

---

# Main Navigation

Bottom Navigation Bar with 5 actions:

1. Home
2. Analysis
3. Add Transaction (Floating Circular Button)
4. Budget
5. Profile

The Add button should be larger and centered.

---

# Home Screen

Purpose:
Give users a quick overview of their financial situation.

## Top Section

Greeting:

"Good Morning, John"

Current date display

Notification icon

---

## Balance Card

Large glassmorphism card showing:

### Current Balance

Formula:

Balance = Total Income - Total Expense

Display:

* Current Balance
* Total Income
* Total Expense

Example:

Balance: $1,250

Income: $2,000
Expense: $750

---

## Quick Statistics

4 mini cards:

* Today's Spending
* This Week Spending
* This Month Spending
* Savings Rate (%)

Example:

Savings Rate = (Income - Expense) / Income

---

## Spending Categories Overview

Horizontal cards showing:

* Food
* Transportation
* Shopping
* Entertainment
* Bills
* Healthcare
* Education
* Other

Display:

* Category Icon
* Amount spent
* Percentage

---

## Recent Transactions

Display latest transactions:

Example:

🍔 Burger King
Food
$8.50
Today

⛽ Fuel
Transportation
$12.00
Yesterday

Features:

* Search transaction
* View all button
* Filter by Income / Expense

---

## Smart Insight Card

Auto-generated spending insights:

Examples:

"You spent 35% more on Food this week."

"Transportation is your largest expense category."

"You are within your monthly budget."

---

# Analysis Screen

Purpose:
Help users understand spending behavior.

---

## Filters

* Daily
* Weekly
* Monthly
* Yearly

Custom Date Range

---

## Spending Trend Chart

Interactive line chart showing:

* Spending trend
* Income trend

---

## Category Breakdown

Donut/Pie Chart

Show:

* Food
* Shopping
* Entertainment
* Transportation
* Bills
* Healthcare
* Education
* Others

Display percentage and amount.

---

## Weekly Breakdown

Example:

Week 1: $120
Week 2: $85
Week 3: $200
Week 4: $95

Bar chart visualization.

---

## Top Spending Categories

Rank categories from highest to lowest.

Example:

1. Food - $250
2. Shopping - $180
3. Transportation - $100

---

## Spending Behavior Analysis

Insights:

* Most active spending day
* Most expensive category
* Average daily spending
* Average weekly spending
* Biggest single transaction
* Number of transactions

---

## Income vs Expense Comparison

Visual comparison chart.

Show:

* Income
* Expense
* Savings

---

# Add Transaction Screen

Floating Action Button opens this page.

---

## Transaction Type

Toggle:

* Income
* Expense

---

## Amount

Numeric input field.

---

## Categories

Expense Categories:

🍔 Food
🚗 Transportation
🛍 Shopping
🎬 Entertainment
💡 Bills
🏥 Healthcare
📚 Education
📦 Others

Income Categories:

💼 Salary
🎁 Bonus
💸 Freelance
📈 Investment
🏦 Savings Return
🎯 Other Income

Display as selectable icon cards.

---

## Date

Date Picker

Default:
Current Date

---

## Note

Optional text area.

Example:

"Dinner with friends"

---

## Receipt Upload

User can:

* Take photo
* Upload image

Store receipt image locally or cloud storage.

---

## Location (Optional)

Save transaction location.

Useful for future analytics.

---

## Save Button

Primary action button.

---

# Budget Screen

Purpose:
Help users manage spending limits.

---

## Monthly Budget

Set monthly spending limit.

Example:

Monthly Budget: $500

Progress bar shows:

Used: $350
Remaining: $150

---

## Category Budgets

Example:

Food: $150
Shopping: $100
Entertainment: $80

Track each category individually.

---

## Recurring Transactions

Create recurring expenses:

Examples:

* Internet Bill
* Electricity Bill
* Water Bill
* Rent
* Netflix

Options:

* Daily
* Weekly
* Monthly
* Yearly

Automatically generate transactions.

---

## Budget Alerts

Notifications:

* 50% reached
* 80% reached
* Budget exceeded

---

## Saving Goal

Allow users to create goals.

Example:

Goal:
New Laptop

Target:
$1,000

Current:
$350

Progress indicator.

---

## Spending Forecast

Predict end-of-month spending using previous patterns.

Example:

"Estimated spending this month: $520"

---

# Profile Screen

Purpose:
Manage user account and app preferences.

---

## User Information

* Profile Picture
* Username
* Email
* Edit Profile

---

## Currency Settings

Support:

* USD ($)
* Cambodian Riel (៛)

User can:

* Switch currency
* Set exchange rate manually

Default:

1 USD = 4100 KHR

---

## Language Settings

Support:

* English
* Khmer

---

## Theme Settings

* Light Mode
* Dark Mode
* System Default

---

## Notifications

Enable/Disable:

* Budget Alerts
* Recurring Payments
* Weekly Reports
* Monthly Reports

---

## Data Management

* Export CSV
* Export PDF Report
* Backup Data
* Restore Data

---

## Security

* Change Password
* Google Account Management
* Biometric Authentication

    * Face ID
    * Fingerprint

---

# Additional Features

## Dashboard Widgets

Quick access cards:

* Add Expense
* Add Income
* View Analysis
* Set Budget

---

## Monthly Report

Generate report showing:

* Total Income
* Total Expense
* Savings
* Top Categories

---

## Transaction Search

Search by:

* Category
* Date
* Note
* Amount

---

## Offline Support

Users can record transactions without internet.

Sync later when online.

---

# Design System

## Color Palette

Primary:
Light Blue (#4A90E2)

Secondary:
Soft Blue (#7FB3FF)

Background:
#F5F8FC

Card:
Glassmorphism White

Accent:
#00C2FF

Success:
#4CAF50

Warning:
#FFB300

Danger:
#F44336

---

## UI Style

* Modern
* Minimal
* Glassmorphism
* Rounded corners (16-24px)
* Soft shadows
* Smooth animations
* Clean typography

---

## Typography

Use one consistent font:

Recommended:

* Inter
* Poppins
* SF Pro Display

Font hierarchy:

* Large headings
* Medium section titles
* Small labels
* Consistent spacing

---

# Technical Requirements

* Responsive mobile design
* Dark mode support
* Local storage/database
* Google Authentication
* Image upload support
* Transaction filtering
* Data visualization charts
* Currency conversion support
* Offline-first architecture

The final application should feel similar in quality to modern fintech apps while remaining simple, clean, and focused on personal expense tracking.
