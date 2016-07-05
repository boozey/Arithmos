package com.nakedape.arithmos;

import java.util.HashMap;

/**
 * Created by Nathan on 6/30/2016.
 */
public class Tutorial {

    private HashMap<String, Lesson> lessons;
    public static final String SIGN_IN_LESSON = "SIGN_IN";
    public static final String MANUAL_SIGN_IN = "MANUAL_SIGN_IN";
    public static final String AUTO_SIGN_IN = "AUTO_SIGN_IN";
    public static final String CHALLENGES = "CHALLENGES";
    public static final String LEVELS = "LEVELS";
    public static final String PLAY_LEVEL_ONE = "PLAY_LEVEL_ONE";
    public static final String LEVEL_TYPES = "LEVEL_TYPES";
    public static final String GOAL_TYPES = "GOAL_TYPES";
    public static final String WAIT_FOR_USER = "WAIT_FOR_USER";
    public static final String SELECTION = "SELECTION";
    public static final String AUTO_SELECTION = "AUTO_SELECTION";
    public static final String MANUAL_SELECTION = "MANUAL_SELECTION";
    public static final String SPECIALS = "SPECIALS";
    public static final String PLAY_LEVEL = "PLAY_LEVEL";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String ACTIVITY = "ACTIVITY";
    public static final String SPECIAL_STORE = "SPECIAL_STORE";
    public static final String ACHIEVEMENTS = "ACHIEVEMENTS";
    public static final String END_TUTORIAL = "END_TUTORIAL";

    public static final int PLAY_LEVEL_BUTTON_ID = 1001;

    public Tutorial(){
        lessons = new HashMap<>(6);
        lessons.put(SIGN_IN_LESSON, new Lesson(R.string.sign_in_lesson, R.string.skip, R.id.sign_in_button, Lesson.BELOW_RIGHT, true, true, MANUAL_SIGN_IN));
        lessons.put(MANUAL_SIGN_IN, new Lesson(R.string.manual_sign_in_lesson, R.id.sign_in_button, true, true, CHALLENGES));
        lessons.put(AUTO_SIGN_IN, new Lesson(R.string.auto_sign_in_lesson, R.id.sign_out_button, true, true, CHALLENGES));
        lessons.put(CHALLENGES, new Lesson(R.string.challenges_button_lesson, R.id.challenges_button, Lesson.BELOW_CENTER, true, true, LEVELS));
        Lesson selectLevelLesson = new Lesson(R.string.level_selection_lesson, R.id.challenges_button, Lesson.BELOW_CENTER, true, true, WAIT_FOR_USER);
        selectLevelLesson.targetId = R.id.challenge_listview;
        lessons.put(LEVELS, selectLevelLesson);
        // GameActivity Lessons
        Lesson levelInfoLesson = new Lesson(R.string.level_types_lesson, R.id.description_textview, Lesson.BELOW_CENTER, true, true, WAIT_FOR_USER);
        levelInfoLesson.targetId = R.id.ok_button;
        lessons.put(LEVEL_TYPES, levelInfoLesson);
        lessons.put(GOAL_TYPES, new Lesson(R.string.goal_types_lesson, R.id.goal_view, Lesson.RIGHT, false, true, SELECTION));
        lessons.put(SELECTION, new Lesson(R.string.selection_lesson, R.string.ok_underlined, R.id.goal_view, Lesson.RIGHT, false, true, WAIT_FOR_USER));
        lessons.put(AUTO_SELECTION, new Lesson(R.string.auto_selection_lesson, R.id.game_board, Lesson.CENTER, false, true, MANUAL_SELECTION));
        lessons.put(MANUAL_SELECTION, new Lesson(R.string.manual_selection_lesson, R.id.show_work_button, Lesson.ABOVE_RIGHT, false, true, SPECIALS));
        lessons.put(SPECIALS, new Lesson(R.string.specials_lesson, R.id.special_layout, Lesson.ABOVE_RIGHT, false, true, PLAY_LEVEL));
        lessons.put(PLAY_LEVEL, new Lesson(R.string.play_level_lesson, R.string.ok_underlined, R.id.game_board, Lesson.CENTER, false, true, WAIT_FOR_USER));
        lessons.put(GAME_OVER, new Lesson(R.string.game_over_lesson, R.id.game_over_popup, Lesson.CENTER, true, true, WAIT_FOR_USER));
        // Back to MainActivity Tutorial
        lessons.put(ACTIVITY, new Lesson(R.string.activity_lesson, R.id.activity_button, Lesson.ABOVE_RIGHT, true, true, SPECIAL_STORE));
        lessons.put(SPECIAL_STORE, new Lesson(R.string.special_store_lesson, R.id.special_store_button, Lesson.ABOVE_CENTER, true, true, ACHIEVEMENTS));
        lessons.put(ACHIEVEMENTS, new Lesson(R.string.achievements_lesson, R.id.achievements_button, Lesson.ABOVE_LEFT, true, true, END_TUTORIAL));
        lessons.put(END_TUTORIAL, new Lesson(R.string.tutorial_complete_lesson, R.string.ok_underlined, R.id.challenges_button, Lesson.CENTER, false, true, WAIT_FOR_USER));
    }

    public Lesson getLesson(String name){
        return lessons.get(name);
    }

    public static class Lesson {
        int textResId;
        int anchorId, targetId;
        int buttonTextResId = R.string.got_it;
        String nextLesson;
        boolean allowTouch, showButton;
        int placementRelToAnchor = BELOW_RIGHT;
        public static final int BELOW_RIGHT = 0, BELOW_CENTER = 1, ABOVE_CENTER = 2,
                RIGHT = 3, CENTER = 4, ABOVE_RIGHT = 5,
                ABOVE_LEFT = 6;

        public Lesson(int anchorId){
            this.anchorId = anchorId;
            this.targetId = anchorId;
        }

        public Lesson(int textResId, int anchorId, boolean allowTouch, boolean showButton, String nextLesson){
            this.textResId = textResId;
            this.anchorId = anchorId;
            this.allowTouch = allowTouch;
            this.showButton = showButton;
            this.nextLesson = nextLesson;
            this.targetId = anchorId;
        }

        public Lesson(int textResId, int anchorId, int placementRelToAnchor, boolean allowTouch, boolean showButton, String nextLesson){
            this.textResId = textResId;
            this.anchorId = anchorId;
            this.allowTouch = allowTouch;
            this.showButton = showButton;
            this.nextLesson = nextLesson;
            this.placementRelToAnchor = placementRelToAnchor;
            this.targetId = anchorId;
        }

        public Lesson(int textResId, int buttonTextResId, int anchorId, int placementRelToAnchor, boolean allowTouch, boolean showButton, String nextLesson){
            this.textResId = textResId;
            this.anchorId = anchorId;
            this.allowTouch = allowTouch;
            this.showButton = showButton;
            this.nextLesson = nextLesson;
            this.placementRelToAnchor = placementRelToAnchor;
            this.buttonTextResId = buttonTextResId;
            this.targetId = anchorId;
        }
    }
}
