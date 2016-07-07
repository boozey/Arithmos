package com.nakedape.arithmos;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
    private int gridSize, goalMode;
    private ArrayList<String> runs;
    private ArrayList<String[]> goalLists;
    private ArrayList<String> runDisplayStrings;
    private int runIndex = 0;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level_designer);
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);

        // Setup defaults
        Spinner goalModeSpinner = (Spinner)rootLayout.findViewById(R.id.goal_mode_spinner);
        GoalTypeAdapter goalTypeAdapter = new GoalTypeAdapter();
        goalModeSpinner.setAdapter(goalTypeAdapter);
        goalModeSpinner.setOnItemSelectedListener(goalTypeSelectListener);

        Spinner gridSizeSpinner = (Spinner)rootLayout.findViewById(R.id.grid_size_spinner);
        GridSizeAdapter gridSizeAdapter = new GridSizeAdapter();
        gridSizeSpinner.setAdapter(gridSizeAdapter);

        EditText gridEditText = (EditText)rootLayout.findViewById(R.id.grid_numbers);
        gridEditText.addTextChangedListener(commaListWatcher);

        EditText goalEditText = (EditText)rootLayout.findViewById(R.id.goal_numbers);
        goalEditText.addTextChangedListener(commaListWatcher);

        runs = new ArrayList<>();
        runDisplayStrings = new ArrayList<>();
        goalLists = new ArrayList<>();

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
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    private TextWatcher commaListWatcher = new TextWatcher() {
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

    // Pre-defined runs
    public void AddRunClick(View v){
        addNewRunItem();
    }
    private void addNewRunItem(){
        LinearLayout runsLayout = (LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout);
        View runView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.predefined_run_list_item, null);

        String run ="";
        runs.add(run);
        String runDisplay = "";
        runDisplayStrings.add(runDisplay);
        goalLists.add(null);

        EditText runEditText = (EditText)runView.findViewById(R.id.run_edittext);
        runEditText.addTextChangedListener(runWatcher);
        runEditText.setTag(runIndex);

        Button checkButton = (Button)runView.findViewById(R.id.check_button);
        checkButton.setTag(runIndex);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkRun((int)v.getTag());
            }
        });

        runsLayout.addView(runView);
        runIndex++;
    }

    private RunWatcher runWatcher = new RunWatcher();

    private class RunWatcher implements TextWatcher {

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
                                }
                            }
                        });
                    }

                }, 1000);
            }
        }
    }

    private String formatGoalList(String[] goals){
        String goalListText = "";
        if (goals != null) {
            for (String s : goals)
                goalListText += s + ", ";
            goalListText = goalListText.substring(0, goalListText.length() - 2);
        }
        return goalListText;
    }

    private void checkRun(int index){
        View runView = ((LinearLayout)rootLayout.findViewById(R.id.runs_linearlayout)).getChildAt(index);
        EditText runEditText = (EditText)runView.findViewById(R.id.run_edittext);
        String runText = runEditText.getText().toString();
        runText = runText.replace("+", ArithmosLevel.BONUS_LOCK_ADD);
        runText = runText.replace("-", ArithmosLevel.BONUS_LOCK_SUB);
        runText = runText.replace("*", ArithmosLevel.BONUS_LOCK_MULT);
        runText = runText.replace("/", ArithmosLevel.BONUS_LOCK_DIV);
        runText = runText.replace(" ", "");
        String[] elements = runText.split(",");
        if (!elements[0].replaceAll("[^0-9]", "").equals(elements[0])){
            // Toast must start with a number
            return;
        } else {
            Log.d(LOG_TAG, runText);
            ArrayList<String> run = new ArrayList<>(elements.length * 2);
            for (int i = 0, j = 0; i < elements.length; i++){
                if (i < elements.length - 1 && elements[i].contains(ArithmosLevel.BONUS_OP_LOCK) && elements[i+1].contains(ArithmosLevel.BONUS_OP_LOCK)) {
                    //Toast Bonus locks must be between two numbers
                    return;
                }
                else {
                    if (i != 0 && !elements[i].contains(ArithmosLevel.BONUS_OP_LOCK) && !run.get(run.size() - 1).contains(ArithmosLevel.BONUS_OP_LOCK))
                        run.add("?");
                    run.add(elements[i]);
                }
            }
            String[] goals = findGoalNumbers(run);
            goalLists.set(index, goals);
            runText = "Possible goals: ";
            for (String s : goals)
                runText += s + ", ";

            EditText goalsEditText = (EditText)runView.findViewById(R.id.goals_edittext);
            goalsEditText.setText(runText);
        }
    }
    public String[] findGoalNumbers(ArrayList<String> run){

        //
        // Create and check all possible expressions
        //

        // Total number of combinations is the number of OPERATIONS to the power of the number of
        // places an operation can be placed
        String[] availableOperations = ArithmosGame.OPERATIONS;

        int numCombinations = (int)Math.pow(availableOperations.length, run.size() - 1);
        ArrayList<String> result = new ArrayList<>(numCombinations);
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
            if (value == (int)value && value >= 0 && !result.contains(String.valueOf((int)value)))
                result.add(String.valueOf((int)value));

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

        // If this statement is reached, all possible combinations of OPERATIONS failed
        String[] goals = new String[result.size()];
        for (int i = 0; i < result.size(); i++)
            goals[i] = result.get(i);
        return goals;
    }

    public static double eval(final String str) {
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

    private void testDesign(){

    }
}
