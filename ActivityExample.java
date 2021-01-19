package it.polimi.mobileapp.triplan.client;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.Fade;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.facebook.AccessToken;
import com.facebook.login.LoginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import it.polimi.mobileapp.triplan.beans.Bill;
import it.polimi.mobileapp.triplan.beans.Expense;
import it.polimi.mobileapp.triplan.beans.Travel;
import it.polimi.mobileapp.triplan.beans.User;
import it.polimi.mobileapp.triplan.client.adapters.RecyclerBalanceAdapter;
import it.polimi.mobileapp.triplan.client.adapters.RecyclerExpenseAdapter;
import it.polimi.mobileapp.triplan.client.fragments.CreditsFragment;
import it.polimi.mobileapp.triplan.client.fragments.LanguageFragment;
import it.polimi.mobileapp.triplan.client.utils.ActivityTracer;
import it.polimi.mobileapp.triplan.client.utils.GlobalApplication;
import it.polimi.mobileapp.triplan.client.utils.SwipeHelper;
import it.polimi.mobileapp.triplan.client.viewmodels.BillViewModel;
import it.polimi.mobileapp.triplan.client.viewmodels.ExpenseViewModel;

public class AccessBillActivity extends BaseActivity {

    private TextView textTitleBill;
    private TextView textBillMembers;
    private TextView textMyTotalAmount;
    private TextView textTripTotalAmount;

    private Button btnAddExpense;
    private ImageButton btnExpensesList;
    private ImageButton btnBalancesList;

    private SwipeRefreshLayout swipeAccessBill;
    private RecyclerView mExpensesRecyclerView;
    private RecyclerExpenseAdapter mExpenseAdapter;
    private RecyclerView mBalancesRecyclerView;
    private RecyclerBalanceAdapter mBalanceAdapter;

    private NestedScrollView mExpensesScrollView;
    private NestedScrollView mBalancesScrollView;
    private View dividerExpensesList;
    private View dividerBalancesList;
    private RelativeLayout layoutAddFirstExpense;

    private BillViewModel mBillViewModel;
    private ExpenseViewModel mExpenseViewModel;
    private Bill mBill;
    private final ArrayList<Expense> mExpensesList = new ArrayList<>();
    private Travel mTravel;
    private final HashMap<String, Float> mapUserNameToCredit = new HashMap<>();

    private boolean shouldAnimateAfterOnBoarding = false;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("TESTING", "DESTROYED:      " + getClass().getSimpleName() + "       -- TASK ID: " + getTaskId());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("TESTING", "CREATED:      " + getClass().getSimpleName() + "       -- TASK ID: " + getTaskId());
        if (getResources().getBoolean(R.bool.isTablet)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        switchAppLanguage(getSharedPreferences("TriplanApplication", MODE_PRIVATE).getString("language", ""));
        setContentView(R.layout.activity_access_bill);

        mBillViewModel      = new ViewModelProvider(this).get(BillViewModel.class);
        mBillViewModel.init();
        mExpenseViewModel   = new ViewModelProvider(this).get(ExpenseViewModel.class);
        mExpenseViewModel.init();

        int travelID        = getIntent().getIntExtra("travelID", 0);

        setupBaseViews(getString(R.string.travel_account));
        setupBaseIcon(4);
        setupBaseListeners(travelID);
        setupDrawer();
        initDrawer(mExpenseViewModel.getUser());
        setupViews();
        setupListeners();
        setupObservers();

        // starts the waterfall of observers
        String toastText    = mBillViewModel.getTravelLocal(travelID);
        if (toastText != null){
            displayToast(toastText);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("TESTING", "RESUMED:      " + getClass().getSimpleName() + "       -- TASK ID: " + getTaskId());
        if (shouldAnimateAfterOnBoarding){
            overridePendingTransition(R.anim.no_anim, R.anim.exit_to_bottom);
            shouldAnimateAfterOnBoarding = false;
        }
        else
            overridePendingTransition(0, 0);
        if (mExpenseAdapter != null)
            mExpenseAdapter.notifyDataSetChanged();
        mExpenseViewModel.cleanExpense();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, TravelList_HOME_Activity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finishAffinity();
    }

    private void setupViews(){
        btnAddExpense           = findViewById(R.id.btnAddExpense);
        textTitleBill           = findViewById(R.id.textTitleBill);
        textBillMembers         = findViewById(R.id.textBillMembers);
        textMyTotalAmount       = findViewById(R.id.textMyTotalAmount);
        textTripTotalAmount     = findViewById(R.id.textTripTotalAmount);
        swipeAccessBill         = findViewById(R.id.swipeAccessBill);

        btnExpensesList         = findViewById(R.id.btnExpensesList);
        btnBalancesList         = findViewById(R.id.btnBalancesList);
        dividerExpensesList     = findViewById(R.id.dividerExpensesList);
        dividerBalancesList     = findViewById(R.id.dividerBalancesList);
        mExpensesRecyclerView   = findViewById(R.id.recyclerViewExpenses);
        mBalancesRecyclerView   = findViewById(R.id.recyclerViewBalances);
        mExpensesScrollView     = findViewById(R.id.scrollViewExpenses);
        mBalancesScrollView     = findViewById(R.id.scrollViewBalances);
        layoutAddFirstExpense   = findViewById(R.id.layoutAddFirstExpense);

        btnExpensesList.setSelected(true);
        btnBalancesList.setSelected(false);
        dividerExpensesList.setVisibility(View.VISIBLE);
        dividerBalancesList.setVisibility(View.INVISIBLE);
        mExpensesScrollView.setVisibility(View.VISIBLE);
        mBalancesScrollView.setVisibility(View.INVISIBLE);
    }

    private void setupListeners() {

        Transition slideLeft    = new Slide(Gravity.START);
        Transition slideRight   = new Slide(Gravity.END);
        TransitionSet transLeft = new TransitionSet().addTransition(new Slide(Gravity.START)).addTransition(new Fade());
        TransitionSet transRight = new TransitionSet().addTransition(new Slide(Gravity.END)).addTransition(new Fade());

        btnExpensesList.setOnClickListener((view) ->{
            if (!view.isSelected()) {
                view.setSelected(true);
                btnBalancesList.setSelected(false);

                TransitionManager.beginDelayedTransition(mExpensesScrollView, transLeft);
                TransitionManager.beginDelayedTransition(findViewById(R.id.layoutDividerExpensesList), slideRight);
                mExpensesScrollView.setVisibility(View.VISIBLE);
                dividerExpensesList.setVisibility(View.VISIBLE);
                TransitionManager.beginDelayedTransition(mBalancesScrollView, transRight);
                TransitionManager.beginDelayedTransition(findViewById(R.id.layoutDividerBalancesList), slideLeft);
                mBalancesScrollView.setVisibility(View.INVISIBLE);
                dividerBalancesList.setVisibility(View.INVISIBLE);
                TransitionManager.beginDelayedTransition(layoutAddFirstExpense, transLeft);
                layoutAddFirstExpense.setVisibility(mExpensesList.isEmpty() ? View.VISIBLE : View.INVISIBLE);
            }
        });

        btnBalancesList.setOnClickListener((view) ->{
            if (!view.isSelected()) {
                view.setSelected(true);
                btnExpensesList.setSelected(false);

                TransitionManager.beginDelayedTransition(mBalancesScrollView, transRight);
                TransitionManager.beginDelayedTransition(findViewById(R.id.layoutDividerBalancesList), slideLeft);
                mBalancesScrollView.setVisibility(View.VISIBLE);
                dividerBalancesList.setVisibility(View.VISIBLE);
                TransitionManager.beginDelayedTransition(mExpensesScrollView, transLeft);
                TransitionManager.beginDelayedTransition(findViewById(R.id.layoutDividerExpensesList), slideRight);
                mExpensesScrollView.setVisibility(View.INVISIBLE);
                dividerExpensesList.setVisibility(View.INVISIBLE);
                TransitionManager.beginDelayedTransition(layoutAddFirstExpense, transLeft);
                layoutAddFirstExpense.setVisibility(View.INVISIBLE);
            }
        });

        swipeAccessBill.setOnRefreshListener(() -> {
            String toastText    = mBillViewModel.getExpensesListServer(mTravel.getToken(), mTravel.getId(), mTravel.getParticipants());
            if (toastText != null){
                displayToast(toastText);
            }
        });

        btnAddExpense.setOnClickListener((view) -> {
            goToAddExpenseActivity();
        });

        mNavigationView.setNavigationItemSelectedListener((item)->{
            switch (item.getTitleCondensed().toString().toLowerCase()){
                case "home":
                    onBackPressed();
                    break;
                case "invite friend":
                    inviteFriend(mTravel.getToken());
                    break;
                case "language":
                    LanguageFragment languageFragment = new LanguageFragment();
                    languageFragment.show(getSupportFragmentManager(), null);
                    break;
                case "tutorial":
                    shouldAnimateAfterOnBoarding = true;
                    Intent intent = new Intent(this, OnBoardingActivity.class);
                    startActivity(intent);
                    break;
                case "credits":
                    CreditsFragment creditsFragment = new CreditsFragment();
                    creditsFragment.show(getSupportFragmentManager(), null);
                    break;
                case "logout":
                    signOut();
                    break;
            }
            mDrawerlayout.closeDrawer(GravityCompat.START);
            return true;
        });

    }

    private void setupObservers(){

        mBillViewModel.getCachedTravel().observe(this, new Observer<Travel>() {
            @Override
            public void onChanged(Travel travel) {
                if (travel != null) {
                    mTravel = travel;
                    textTitleBill.setText(mTravel.getTitle());
                    ArrayList<String> members = new ArrayList<>();
                    for (User u : mTravel.getParticipants()) {
                        members.add(u.getName());
                    }
                    textBillMembers.setText(members.toString().replace("[", "").replace("]", ""));
                    initExpenseRecyclerView();
                    initBalanceRecyclerView();

                    String toastText = mBillViewModel.getExpensesListLocal(mTravel.getToken());
                    if (toastText != null) {
                        displayToast(toastText);
                    }
                    toastText = mBillViewModel.getBillLocal(mTravel.getToken());
                    if (toastText != null) {
                        displayToast(toastText);
                    }
                    mBillViewModel.getCachedTravel().removeObserver(this);
                }
            }
        });

        mBillViewModel.getCachedExpensesList().observe(this, new Observer<ArrayList<Expense>>() {
            @Override
            public void onChanged(ArrayList<Expense> expenses) {
                if (expenses != null) {
                    refreshUI();
                    mExpensesList.clear();
                    mExpensesList.addAll(expenses);
                    mExpenseAdapter.notifyDataSetChanged();
                    TransitionSet transLeft = new TransitionSet().addTransition(new Slide(Gravity.START)).addTransition(new Fade());
                    if (btnExpensesList.isSelected()) {
                        TransitionManager.beginDelayedTransition(layoutAddFirstExpense, transLeft);
                        layoutAddFirstExpense.setVisibility(expenses.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                    }
                    swipeAccessBill.setRefreshing(false);
                }
            }
        });

        mBillViewModel.getCachedBillsList().observe(this, new Observer<ArrayList<Bill>>() {
            @Override
            public void onChanged(ArrayList<Bill> bills) {
                if (bills != null && !bills.isEmpty()) {
                    // initialize the total trip amount
                    float tripTotal = 0;
                    // get a simple map int->string to have a corrispondence betweene unique ID stored in database and the username
                    HashMap<Integer, String> mapIDtoUserName = mExpenseViewModel.getMapIDtoUserName(mTravel.getParticipants());
                    mapUserNameToCredit.clear();
                    for (Bill bill : bills) {
                        // create a simple map string->float to have a corrispondence between a user and his credit
                        mapUserNameToCredit.put(bill.getUser_id()+"/"+(mapIDtoUserName.get(bill.getUser_id())), bill.getCredit());
                        tripTotal += bill.getBalance();
                        if (bill.getUser_id() == mExpenseViewModel.getUser().getId())
                            mBill = bill;
                    }

                    String total = String.format(("%.2f"), tripTotal);
                    if (Float.parseFloat(total.replace(',', '.')) == 0)
                        total = "0.00";
                    textTripTotalAmount.setText(total + " €");
                    String balance = String.format(("%.2f"), mBill.getBalance());
                    if (Float.parseFloat(balance.replace(',', '.')) == 0)
                        balance = "0.00";
                    textMyTotalAmount.setText(balance + " €");

                    mBalanceAdapter.notifyDataSetChanged();
                } else {
                    // no expenses are found: set everything to 0
                    if (bills != null && bills.isEmpty()) {
                        HashMap<Integer, String> mapIDtoUserName = mExpenseViewModel.getMapIDtoUserName(mTravel.getParticipants());
                        mapUserNameToCredit.clear();
                        for (User user : mTravel.getParticipants()) {
                            mapUserNameToCredit.put(user.getId()+"/"+(mapIDtoUserName.get(user.getId())), Float.valueOf("0.00"));
                        }
                    }
                    textTripTotalAmount.setText("0.00 €");
                    textMyTotalAmount.setText("0.00 €");
                }
                swipeAccessBill.setRefreshing(false);
            }
        });

        mBillViewModel.getCachedBill().observe(this, new Observer<Bill>() {
            @Override
            public void onChanged(Bill bill) {
                if (bill != null) {
                    mBill = bill;
                    String balance = String.format(("%.2f"), mBill.getBalance());
                    if (Float.parseFloat(balance.replace(',', '.')) == 0)
                        balance = "0.00";
                    textMyTotalAmount.setText(balance + " €");
                }
            }
        });

        mBillViewModel.getCachedError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s != null) {
                    swipeAccessBill.setRefreshing(false);
                    displayToast(s);
                    mBillViewModel.cleanMutable();
                }
            }
        });

        mExpenseViewModel.getCachedError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s != null) {
                    swipeAccessBill.setRefreshing(false);
                    displayToast(s);
                    mExpenseViewModel.cleanMutable();
                }
            }
        });
    }

    // initialize and feed for the first time the recyclerView
    private void initBalanceRecyclerView(){

        mBalanceAdapter = new RecyclerBalanceAdapter(this, mapUserNameToCredit);
        RecyclerView.LayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mBalancesRecyclerView.setLayoutManager(linearLayoutManager);
        mBalancesRecyclerView.setAdapter(mBalanceAdapter);
    }

    // initialize and feed for the first time the recyclerView
    private void initExpenseRecyclerView(){

        mExpenseAdapter = new RecyclerExpenseAdapter(this, mExpensesList,
                            mTravel.getId(), mExpenseViewModel.getMapIDtoUserName(mTravel.getParticipants()));
        RecyclerView.LayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mExpensesRecyclerView.setLayoutManager(linearLayoutManager);
        mExpensesRecyclerView.setAdapter(mExpenseAdapter);

        // Create and attach listener for swipe gestures
        new SwipeHelper(GlobalApplication.getAppContext(), mExpensesRecyclerView) {
            @Override
            public void instantiateUnderlayButton(RecyclerView.ViewHolder viewHolder, List<UnderlayButton> underlayButtons) {
                underlayButtons.add(new SwipeHelper.UnderlayButton(
                        R.drawable.ic_btn_delete_item,
                        Color.parseColor("#EA4F51"),
                        new SwipeHelper.UnderlayButtonClickListener() {
                            @Override
                            public void onClick(int pos) {
                                String toastText = mExpenseViewModel.removeExpense(mExpensesList.get(pos).getToken(),
                                            mExpensesList.get(pos).getAmount() * mExpensesList.get(pos).getRecipients().size(),
                                            mExpensesList.get(pos).isRefund_flag(),
                                            mExpensesList.get(pos).getPayer_id(),
                                            mExpensesList.get(pos).getRecipients(),
                                            mExpensesList.get(pos).getTravel_token());
                                if (toastText != null){
                                    displayToast(toastText);
                                }
                                mExpenseAdapter.notifyItemRemoved(pos);
                            }
                        }
                ));

                underlayButtons.add(new SwipeHelper.UnderlayButton(
                        R.drawable.ic_btn_edit_item,
                        Color.parseColor("#FCC04E"),
                        new SwipeHelper.UnderlayButtonClickListener() {
                            @Override
                            public void onClick(int pos) {
                                goToEditExpenseActivity(mExpensesList.get(pos).getToken());
                            }
                        }
                ));
            }
        };
    }

    // fetch data from the server
    private void refreshUI(){
        String toastText    = mBillViewModel.getTravelBillsListServer(mTravel.getToken(), mTravel.getId(), mTravel.getParticipants());
        if (toastText != null){
            displayToast(toastText);
        }
    }

    private void goToAddExpenseActivity() {
        ActivityTracer.setBillStatus(ActivityTracer.MyAppStatus.ADD_EXPENSE);
        Intent intent = new Intent(this, AddExpenseActivity.class);
        intent.putExtra("travelID", mTravel.getId());
        startActivity(intent);
    }

    private void goToEditExpenseActivity(String expenseToken){
        ActivityTracer.setBillStatus(ActivityTracer.MyAppStatus.EDIT_EXPENSE);
        Intent intent = new Intent(this, EditExpenseActivity.class);
        intent.putExtra("expenseToken", expenseToken);
        intent.putExtra("travelID", mTravel.getId());
        startActivity(intent);
    }

    private void signOut() {

        // FACEBOOK
        LoginManager.getInstance().logOut();
        AccessToken.setCurrentAccessToken(null);

        // GOOGLE
        mGoogleSignInClient.signOut();

        // GENERAL
        getSharedPreferences("TriplanApplication", MODE_PRIVATE).edit().remove("isLogged").apply();
        getSharedPreferences("TriplanApplication", MODE_PRIVATE).edit().remove("user_id").apply();
        mExpenseViewModel.signOut();
        goToLoginActivity();
    }

    private void switchAppLanguage(String lang) {
        Configuration config = getBaseContext().getResources().getConfiguration();
        if (!"".equals(lang) && !config.getLocales().get(0).getLanguage().equals(lang)) {
            getSharedPreferences("TriplanApplication", MODE_PRIVATE).edit().putString("language", lang).apply();
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Configuration conf = new Configuration(config);
            conf.setLocale(locale);
            getBaseContext().getResources().updateConfiguration(conf, getBaseContext().getResources().getDisplayMetrics());
        }
    }

    // custom toast design
    private void displayToast(String toastText){
        View layoutToast = getLayoutInflater().inflate(R.layout.custom_toast, findViewById(R.id.toastLayout));
        TextView textDisplayToast = layoutToast.findViewById(R.id.textDisplayToast);
        textDisplayToast.setText(toastText);
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layoutToast);
        toast.show();
    }

}
