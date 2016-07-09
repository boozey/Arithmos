package com.nakedape.arithmos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class LevelDesignerActivity extends AppCompatActivity {
    private static final String LOG_TAG = "LevelDesignerActivity";

    private RelativeLayout rootLayout;
    private int gridSize = 4, goalMode = ArithmosLevel.GOAL_MULT_NUM;
    private ArrayList<String> goalList;
    private ArrayList<String[]> runs;
    private ArrayList<String[]> goalLists;
    private String[] gridNumbers;
    private int runIndex = 0;
    private Timer timer;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level_designer);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);

        // Setup defaults
        Spinner goalModeSpinner = (Spinner)rootLayout.findViewById(R.id.goal_mode_spinner);
        GoalTypeAdapter goalTypeAdapter = new GoalTypeAdapter();
        goalModeSpinner.setAdapter(goalTypeAdapter);
        goalModeSpinner.setOnItemSelectedListener(goalTypeSelectListener);

        Spinner gridSizeSpinner = (Spinner)rootLayout.findViewById(R.id.grid_size_spinner);
        GridSizeAdapter gridSizeAdapter = new GridSizeAdapter();
        gridSizeSpinner.setAdapter(gridSizeAdapter);
        gridSizeSpinner.setOnItemSelectedListener(gridSizeSelectListener);

        EditText gridEditText = (EditText)rootLayout.findViewById(R.id.grid_numbers);
        gridEditText.addTextChangedListener(gridTextWatcher);

        EditText goalEditText = (EditText)rootLayout.findViewById(R.id.goal_numbers);
        goalEditText.addTextChangedListener(goalTextWatcher);

        runs = new ArrayList<>();
        goalLists = new ArrayList<>();

        EditText bonusEditText = (EditText)rootLayout.findViewById(R.id.apple_count);
        bonusEditText.addTextChangedListener(bonusWatcher);
        bonusEditText = (EditText)rootLayout.findViewById(R.id.banana_count);
        bonusEditText.addTextChangedListener(bonusWatcher);
        bonusEditText = (EditText)rootLayout.findViewById(R.id.bomb_count);
        bonusEditText.addTextChangedListener(bonusWatcher);
        bonusEditText = (EditText)rootLayout.findViewById(R.id.cherry_count);
        bonusEditText.addTextChangedListener(bonusWatcher);
        bonusEditText = (EditText)rootLayout.findViewById(R.id.balloon_count);
        bonusEditText.addTextChangedListener(bonusWatcher);

    }

    // Grid & goal numbers and goal types
    private class GoalTypeAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private int[] goalTypes = {R.string.goal_mult, R.string.goal_301, R.string.goal_bouncing};
        private int resource_id = R.layout.game_options_spinner_dropdown_item;

        @Override
        public int getCount() {
            return goalTypes.length;
        }

        @Override
        public Object getItem(int position) {
            return goalTypes[position];
        }

        @Override
        public long getItemId(int position) {
            return goalTypes[position];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            TextView textView = (TextView)convertView.findViewById(R.id.textView1);
            textView.setText(goalTypes[position]);
            return convertView;
        }

    }

    private AdapterView.OnItemSelectedListener goalTypeSelectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch ((int)parent.getItemIdAtPosition(position)){
                case R.string.goal_301:
                    rootLayout.findViewById(R.id.goal_numbers).setVisibility(View.GONE);
                    goalMode = ArithmosLevel.GOAL_301;
                    break;
                case R.string.goal_mult:
                    rootLayout.findViewById(R.id.goal_numbers).setVisibility(View.VISIBLE);
                    goalMode = ArithmosLevel.GOAL_MULT_NUM;
                    break;
                case R.string.goal_bouncing:
                    goalMode = ArithmosLevel.GOAL_SINGLE_NUM;
                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    private class GridSizeAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private int[] gridSizeIds = {R.string.grid_size_4, R.string.grid_size_5, R.string.grid_size_6, R.string.grid_size_7, R.string.grid_size_8,
        R.string.grid_size_9, R.string.grid_size_10};
        private int resource_id = R.layout.game_options_spinner_dropdown_item;

        @Override
        public int getCount() {
            return gridSizeIds.length;
        }

        @Override
        public Object getItem(int position) {
            return gridSizeIds[position];
        }

        @Override
        public long getItemId(int position) {
            return gridSizeIds[position];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            TextView textView = (TextView)convertView.findViewById(R.id.textView1);
            textView.setText(gridSizeIds[position]);
            return convertView;
        }

    }

    private AdapterView.OnItemSelectedListener gridSizeSelectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch ((int)parent.getItemIdAtPosition(position)){
                case R.string.grid_size_4:
                    gridSize = 4;
                    break;
                case R.string.grid_size_5:
                    gridSize = 5;
                    break;
                case R.string.grid_size_6:
                    gridSize = 6;
                    break;
                case R.string.grid_size_7:
                    gridSize = 7;
                    break;
                case R.string.grid_size_8:
                    gridSize = 8;
                    break;
                case R.string.grid_size_9:
                    gridSize = 9;
                    break;
                case R.string.grid_size_10:
                    gridSize = 10;
                    break;
            }
            generatePreview();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    private TextWatcher gridTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(timer != null)
                timer.cancel();
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (s.length() > 0) {
                if (timer != null) timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String text = s.toString().trim();
                                String origText = text;
                                text = text.replaceAll("\\D", ", ");
                                while (text.contains("  "))
                                    text = text.replace("  ", " ");
                                while (text.contains(" ,"))
                                    text = text.replace(" ,", ",");
                                while (text.contains(",,"))
                                    text = text.replace(",,", ",");
                                if (text.endsWith(","))
                                    text += " ";
                                if (!text.endsWith(", "))
                                    text += ", ";
                                if (text.startsWith(" "))
                                    text = text.substring(1, text.length() - 1);
                                if (text.startsWith(","))
                                    text = text.substring(1, text.length() - 1);
                                if (!text.equals(origText)) {
                                    s.replace(0, s.length(), text);
                                    timer.cancel();
                                    generatePreview();
                                }
                            }
                        });
                    }

                }, 1000);
            }
        }
    };

    private TextWatcher goalTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(timer != null)
                timer.cancel();
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (s.length() > 0) {
                if (timer != null) timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String text = s.toString().trim();
                                String origText = text;
                                text = text.replaceAll("\\D", ", ");
                                while (text.contains("  "))
                                    text = text.replace("  ", " ");
                                while (text.contains(" ,"))
                                    text = text.replace(" ,", ",");
                                while (text.contains(",,"))
                                    text = text.replace(",,", ",");
                                if (text.endsWith(","))
                                    text += " ";
                                if (!text.endsWith(", "))
                                    text += ", ";
                                if (text.startsWith(" "))
                                    text = text.substring(1, text.length() - 1);
                                if (text.startsWith(","))
                                    text = text.substring(1, text.length() - 1);
                                if (!text.equals(origText)) {
                                    s.replace(0, s.length(), text);
                                    timer.cancel();
                                }
                            }
                        });
                    }

                }, 1000);
            }
        }
    };

    private boolean updateGridNumList(){
        EditText gridNumEditText = (EditText)rootLayout.findViewById(R.id.grid_numbers);
        String text = gridNumEditText.getText().toString().trim();
        text = text.replace(" ", "");
        String[] elements = text.split(",");
        ArrayList<String> numList = new ArrayList<>(elements.length);
        for (String s : elements) {
            if (!s.equals(" ") && !s.equals("")) {
                numList.add(s);
            }
        }
        gridNumbers = new String[numList.size()];
        for (int i = 0; i < numList.size(); i++){
            gridNumbers[i] = numList.get(i);
        }

        if (gridNumbers.length < 1) gridNumEditText.setError(getString(R.string.error_goal_list_length));

        return gridNumbers.length > 0;
    }

    // Bonuses
    private TextWatcher bonusWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            generatePreview();
        }
    };

    // Pre-defined runs
    public void ShowRunsClick(View v){
        v.setSelected(!v.isSelected());
        GamePreview gamePreview = (GamePreview)rootLayout.findViewById(R.id.game_board_preview);
        gamePreview.setShowRuns(v.isSelected());
    }

    public void AddRunClick(View v){
        addNewRunItem();
    }

    private class RunWatcher implements TextWatcher {

        int index;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(timer != null)
                timer.cancel();
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (s.length() > 0) {
                if (timer != null) timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String text = s.toString().trim();
                                String origText = text;
                                text = text.replaceAll("[^0-9\\+\\-\\*/]", ", ");
                                while (text.contains("  "))
                                    text = text.replace("  ", " ");
                                while (text.contains(" ,"))
                                    text = text.replace(" ,", ",");
                                while (text.contains(",,"))
                                    text = text.replace(",,", ",");
                                if (text.endsWith(","))
                                    text += " ";
                                if (!text.endsWith(", "))
                                    text += ", ";
                                if (!text.equals(origText)) {
                                    s.clear();
                                    s.append(text);
                                    timer.cancel();
                                    updateRun(s);
                                    generatePreview();
                                }
                            }
                        });
                    }

                }, 1000);
            }
        }
    }

    private RunWatcher runWatcher = new RunWatcher();

    private void addNewRunItem(){
        final LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        int index = runsLayout.getChildCount();
        final View runView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.predefined_run_list_item, null);
        runView.setTag(index);

        String[] run = new String[]{""};
        runs.add(run);
        goalLists.add(null);

        EditText runEditText = (EditText)runView.findViewById(R.id.run_edittext);

        runEditText.addTextChangedListener(runWatcher);

        View removeButton = runView.findViewById(R.id.remove_button);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runsLayout.removeView(runView);
                indexRuns();
            }
        });

        runsLayout.addView(runView);
    }

    private void indexRuns(){
        LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        for (int i = 0; i < runsLayout.getChildCount(); i++){
            runsLayout.getChildAt(i).setTag(i);
        }
        generatePreview();
        updateGoalList();
    }

    private boolean updateGoalList(){
        EditText goalListEditText = (EditText)rootLayout.findViewById(R.id.goal_numbers);
        String goalListText = goalListEditText.getText().toString().trim();
        goalListText = goalListText.replace(" ", "");
        String[] elements = goalListText.split(",");
        goalList = new ArrayList<>(elements.length);
        for (String s : elements) {
            if (!s.equals(" ") && !s.equals("")) {
                goalList.add(s);
            }
        }
        return goalList.size() > 0;
    }

    private void addRemoveGoal(String goal){
        // Add or remove goal to lists as appropriate
        updateGoalList();
        EditText goalsEditText = (EditText)rootLayout.findViewById(R.id.goal_numbers);
        String text = goalsEditText.getText().toString();
        if (goalList.contains(goal)){
            goalList.remove(goal);
            text = text.replace(goal, "");
            goalsEditText.setText(text);
        } else {
            goalList.add(goal);
            text += goal + ", ";
            goalsEditText.setText(text);
        }
        // Update color coding in pre-defined runs
        updateGoalList();
        LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        for (int i = 0; i < runsLayout.getChildCount(); i++){
            View v = runsLayout.getChildAt(i);
            LinearLayout goalsLayout = (LinearLayout)v.findViewById(R.id.goal_layout);
            for (int j = 0; j < goalsLayout.getChildCount(); j++){
                TextView textView = (TextView)goalsLayout.getChildAt(j);
                String number = textView.getText().toString();
                if (goalList.contains(number)) {
                    textView.setSelected(true);
                    textView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_light, null));
                }
                else {
                    textView.setSelected(false);
                    textView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
                }
            }
        }
    }

    private void updateRun(Editable s){
        LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        for (int i = 0; i < runsLayout.getChildCount(); i++){
            View runView = runsLayout.getChildAt(i);
            EditText editText = (EditText)runView.findViewById(R.id.run_edittext);
            if (editText.getText().hashCode() == s.hashCode()) {
                checkRun((int)runView.getTag());
                return;
            }
        }
    }

    private void checkRun(int index){
        // Get goal string and format it
        View runView = ((LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout)).getChildAt(index);
        EditText runEditText = (EditText)runView.findViewById(R.id.run_edittext);
        runEditText.setError(null);
        String runText = runEditText.getText().toString();
        runText = runText.replace("+", ArithmosLevel.BONUS_LOCK_ADD);
        runText = runText.replace("-", ArithmosLevel.BONUS_LOCK_SUB);
        runText = runText.replace("*", ArithmosLevel.BONUS_LOCK_MULT);
        runText = runText.replace("/", ArithmosLevel.BONUS_LOCK_DIV);
        runText = runText.replace(" ", "");
        String[] elements = runText.split(",");
        if (!elements[0].replaceAll("[^0-9]", "").equals(elements[0])){
            // Error must start with a number
            runEditText.setError(getString(R.string.error_start_with_number));
            return;
        } else {
            Log.d(LOG_TAG, runText);
            ArrayList<String> run = new ArrayList<>(elements.length * 2);
            for (int i = 0, j = 0; i < elements.length; i++){
                if ((i < elements.length - 1 && elements[i].contains(ArithmosLevel.BONUS_OP_LOCK) && elements[i+1].contains(ArithmosLevel.BONUS_OP_LOCK))
                        || (i == elements.length - 1 && elements[i].contains(ArithmosLevel.BONUS_OP_LOCK))) {
                    //Error Bonus locks must be between two numbers
                    runEditText.setError(getString(R.string.error_lock_between));
                    return;
                }
                else {
                    if (i != 0 && !elements[i].contains(ArithmosLevel.BONUS_OP_LOCK) && !run.get(run.size() - 1).contains(ArithmosLevel.BONUS_OP_LOCK))
                        run.add("?");
                    run.add(elements[i]);
                }
            }

            // Find possible goal numbers and record
            if (run.size() < 3) {
                runEditText.setError(getString(R.string.error_run_length));
                return;
            }
            String[] goals = findGoalNumbers(run);
            goalLists.set(index, goals);

            // Update level goal list
            updateGoalList();

            // Display and color code possible goal numbers for the run
            LinearLayout goalLayout = (LinearLayout)runView.findViewById(R.id.goal_layout);
            goalLayout.removeAllViews();
            for (final String goal : goals){
                TextView goalView = new TextView(context);
                goalView.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.goal_suggestion_background, null));
                goalView.setPadding(8, 0, 8, 0);
                goalView.setMinWidth(40);
                goalView.setText(goal);
                goalView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                if (goalList.contains(goal)) {
                    goalView.setSelected(true);
                    goalView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_light, null));
                }
                else {
                    goalView.setSelected(false);
                    goalView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(8, 0, 0, 0);
                goalView.setLayoutParams(params);
                goalView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addRemoveGoal(goal);
                    }
                });
                goalLayout.addView(goalView);
            }
        }
    }

    private void updateRunList(){
        runs = new ArrayList<>();
        LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        for (int i = 0; i < runsLayout.getChildCount(); i++){
            // Get goal string and format it
            View runView = ((LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout)).getChildAt(i);
            EditText runEditText = (EditText)runView.findViewById(R.id.run_edittext);
            runEditText.setError(null);
            String runText = runEditText.getText().toString();
            runText = runText.replace("+", ArithmosLevel.BONUS_LOCK_ADD);
            runText = runText.replace("-", ArithmosLevel.BONUS_LOCK_SUB);
            runText = runText.replace("*", ArithmosLevel.BONUS_LOCK_MULT);
            runText = runText.replace("/", ArithmosLevel.BONUS_LOCK_DIV);
            runText = runText.replace(" ", "");
            String[] elements = runText.split(",");
            if (elements.length < 1){
                return;
            }
            else if (!elements[0].replaceAll("[^0-9]", "").equals(elements[0])){
                // Error must start with a number
                runEditText.setError(getString(R.string.error_start_with_number));
            } else {
                boolean addToList = true;
                Log.d(LOG_TAG, runText);
                ArrayList<String> runList = new ArrayList<>(elements.length * 2);
                for (int j = 0; j < elements.length; j++) {
                    if ((j < elements.length - 1 && elements[j].contains(ArithmosLevel.BONUS_OP_LOCK) && elements[j + 1].contains(ArithmosLevel.BONUS_OP_LOCK))
                            || (j == elements.length - 1 && elements[j].contains(ArithmosLevel.BONUS_OP_LOCK))) {
                        //Error Bonus locks must be between two numbers
                        runEditText.setError(getString(R.string.error_lock_between));
                        addToList = false;
                        break;
                    } else {
                        if (j != 0 && !elements[j].contains(ArithmosLevel.BONUS_OP_LOCK) && !runList.get(runList.size() - 1).contains(ArithmosLevel.BONUS_OP_LOCK))
                            runList.add("?");
                        runList.add(elements[j]);
                    }
                }
                if (addToList) {
                    if (runList.size() >= 3) {
                        String[] run = new String[runList.size()];
                        for (int j = 0; j < runList.size(); j++) {
                            run[j] = runList.get(j);
                        }
                        runs.add(run);
                    } else {
                        runEditText.setError(getString(R.string.error_run_length));
                    }
                }
            }
        }
    }

    private String[] findGoalNumbers(ArrayList<String> run1){

        ArrayList<String> run2 = new ArrayList<>(run1.size());
        for (int i = run1.size() - 1; i >= 0; i--)
            run2.add(run1.get(i));

        ArrayList<ArrayList<String>> runs = new ArrayList<>(2);
        runs.add(run1);
        runs.add(run2);

        ArrayList<String> result = new ArrayList<>(10);

        for (ArrayList<String> run : runs) {

            //
            // Create and check all possible expressions
            //

            // Total number of combinations is the number of OPERATIONS to the power of the number of
            // places an operation can be placed
            String[] availableOperations = ArithmosGame.OPERATIONS;

            int numCombinations = (int) Math.pow(availableOperations.length, run.size() - 1);
            // Array of values selected with space to insert OPERATIONS in between each pair of values
            String[] baseExp = new String[run.size()];
            // One counter for each location an operation can be placed.  Num OPERATIONS is one less
            // than the number of values selected
            int[] counters = new int[(run.size() - 1) / 2];

            // Loop until all possible combinations have been checked
            for (int count = 0; count < numCombinations; count++) {
                // Loop through selected values and insert next combination of OPERATIONS
                for (int i = 0, j = 0; i < baseExp.length; i += 2) {
                    baseExp[i] = run.get(i);
                    // Fill position with operation
                    if (j < counters.length) {
                        baseExp[i + 1] = availableOperations[counters[j]];

                        // Replace operation if it is a lock bonus
                        if (run.get(i + 1).contains(ArithmosLevel.BONUS_OP_LOCK)) {
                            baseExp[i + 1] = run.get(i + 1).replace(ArithmosLevel.BONUS_OP_LOCK, "");
                        }
                        j++;
                    }
                }

                // Build expression string to check from array
                String exp = "";
                for (String s : baseExp)
                    exp += s + " ";

                // Check if expression evaluates to a whole number and record if it does
                Log.d(LOG_TAG, exp);
                double value = eval(exp);
                if (value == (int) value && value >= 0 && value < 100 && !result.contains(String.valueOf((int) value)))
                    result.add(String.valueOf((int) value));

                // Increment counters to try next combination of OPERATIONS
                // Counters increment modulo the number of OPERATIONS
                int index = 0;
                boolean carry = false;
                do {
                    counters[index]++;
                    if (counters[index] == availableOperations.length) {
                        counters[index] = 0;
                        carry = true;
                    } else {
                        carry = false;
                    }
                    index++;
                } while (carry && index < counters.length);
            } // End main loop
        }
        // When this statement is reached, all possible combinations of OPERATIONS have been checked
        String[] goals = new String[result.size()];
        for (int i = 0; i < result.size(); i++)
            goals[i] = result.get(i);
        return goals;
    }

    private static double eval(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            // Grammar:
            // expression = term | expression `+` term | expression `-` term
            // term = factor | term `*` factor | term `/` factor
            // factor = `+` factor | `-` factor | `(` expression `)`
            //        | number | functionName factor | factor `^` factor

            double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) x /= parseFactor(); // division
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor(); // unary plus
                if (eat('-')) return -parseFactor(); // unary minus

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') { // functions
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    x = parseFactor();
                    if (func.equals("sqrt")) x = Math.sqrt(x);
                    else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                    else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                    else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                    else throw new RuntimeException("Unknown function: " + func);
                } else {
                    throw new RuntimeException("Unexpected: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation

                return x;
            }
        }.parse();
    }

    // Preview and Testing
    private void generatePreview(){
        EditText gridNumsEditText = (EditText)rootLayout.findViewById(R.id.grid_numbers);
        gridNumsEditText.setError(null);
        EditText goalsEditText = (EditText)rootLayout.findViewById(R.id.goal_numbers);
        goalsEditText.setError(null);
        if (updateGridNumList()) {
            updateRunList();
            // Create bonus hashmap
            HashMap<String, Integer> bonuses = new HashMap<>(5);
            EditText editText = (EditText) rootLayout.findViewById(R.id.apple_count);
            String text = editText.getText().toString();
            if (!text.equals("") && !text.equals("0"))
                bonuses.put(ArithmosLevel.BONUS_APPLE, Integer.valueOf(text));

            editText = (EditText) rootLayout.findViewById(R.id.banana_count);
            text = editText.getText().toString();
            if (!text.equals("") && !text.equals("0"))
                bonuses.put(ArithmosLevel.BONUS_BANANAS, Integer.valueOf(text));

            editText = (EditText) rootLayout.findViewById(R.id.bomb_count);
            text = editText.getText().toString();
            if (!text.equals("") && !text.equals("0"))
                bonuses.put(ArithmosLevel.BONUS_BOMB, Integer.valueOf(text));

            editText = (EditText) rootLayout.findViewById(R.id.cherry_count);
            text = editText.getText().toString();
            if (!text.equals("") && !text.equals("0"))
                bonuses.put(ArithmosLevel.BONUS_CHERRIES, Integer.valueOf(text));

            editText = (EditText) rootLayout.findViewById(R.id.balloon_count);
            text = editText.getText().toString();
            if (!text.equals("") && !text.equals("0"))
                bonuses.put(ArithmosLevel.BONUS_BALLOONS, Integer.valueOf(text));

            GamePreview gamePreview = (GamePreview) rootLayout.findViewById(R.id.game_board_preview);
            gamePreview.setupBoard(gridSize, gridNumbers, bonuses, runs);

            View showRunsView = rootLayout.findViewById(R.id.show_runs_button);
            gamePreview.setShowRuns(showRunsView.isSelected());
        }

    }

    private void playGame(){

    }
}
