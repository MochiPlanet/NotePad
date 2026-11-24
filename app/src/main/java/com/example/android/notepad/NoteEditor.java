package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Creates or edits a single note.
 *
 * <p>The screen keeps the "save automatically when leaving" behaviour of the original sample,
 * and adds a category picker, a pin toggle, a live word count and a delete button.</p>
 */
public class NoteEditor extends Activity {

    /** Optional extra used by {@link NotesList} when creating a note from a filtered category. */
    public static final String EXTRA_CATEGORY = "com.example.android.notepad.extra.CATEGORY";

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private static final String STATE_URI = "state_uri";
    private static final String STATE_MODE = "state_mode";

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                          // 0
            NotePad.Notes.COLUMN_NAME_TITLE,            // 1
            NotePad.Notes.COLUMN_NAME_NOTE,             // 2
            NotePad.Notes.COLUMN_NAME_CATEGORY,         // 3
            NotePad.Notes.COLUMN_NAME_PINNED,           // 4
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,      // 5
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE // 6
    };

    private int mState = STATE_EDIT;
    private Uri mUri;

    private EditText mTitleInput;
    private EditText mNoteInput;
    private TextView mCategoryChip;
    private TextView mEditorTime;
    private TextView mEditorTitle;
    private TextView mWordCount;
    private ImageButton mPinButton;

    private String mCategory = NotePad.Notes.CATEGORY_DEFAULT;
    private boolean mPinned;
    private boolean mLoaded;
    private boolean mDeleted;

    private long mCreated;
    private long mModified;

    // Values loaded from the database, used to skip pointless updates.
    private String mOriginalTitle = "";
    private String mOriginalContent = "";
    private String mOriginalCategory = NotePad.Notes.CATEGORY_DEFAULT;
    private boolean mOriginalPinned;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        boolean restoring = savedInstanceState != null
                && savedInstanceState.getString(STATE_URI) != null;

        if (restoring) {
            mUri = Uri.parse(savedInstanceState.getString(STATE_URI));
            mState = savedInstanceState.getInt(STATE_MODE, STATE_EDIT);
        } else if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
        } else {
            Toast.makeText(this, R.string.toast_unknown_action, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_note_editor);
        getWindow().setStatusBarColor(getResources().getColor(R.color.primary));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.primary_dark));
        useLightStatusBarIcons();

        bindViews();
        setUpActions();

        if (mState == STATE_INSERT && !restoring) {
            mCategory = NoteUtils.normalizeCategory(
                    intent.getStringExtra(EXTRA_CATEGORY));
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);
            mUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
            if (mUri == null) {
                Toast.makeText(this, R.string.toast_create_failed, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        loadNote();

        if (mLoaded && mState == STATE_INSERT && !restoring) {
            // The provider created an empty row; show blank fields so the user starts clean.
            mTitleInput.setText("");
            mNoteInput.setText("");
            mOriginalTitle = "";
            mOriginalContent = "";
            if (Intent.ACTION_PASTE.equals(action)) {
                pasteFromClipboard();
            }
            updateMeta();
        }

        if (mLoaded) {
            if (mState == STATE_INSERT) {
                focusNoteInputForTyping();
            } else {
                // Existing notes open in a calm reading state; the keyboard appears on a tap.
                findViewById(R.id.editor_root).requestFocus();
                mTitleInput.clearFocus();
                mNoteInput.clearFocus();
            }
        }
    }

    /** New notes go straight to the content field with the keyboard ready. */
    private void focusNoteInputForTyping() {
        mNoteInput.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                mNoteInput.requestFocus();
                mNoteInput.setSelection(mNoteInput.getText().length());
                InputMethodManager manager = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) {
                    manager.showSoftInput(mNoteInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 200);
    }

    private void bindViews() {
        mTitleInput = (EditText) findViewById(R.id.editor_title_input);
        mNoteInput = (EditText) findViewById(R.id.editor_note_input);
        mCategoryChip = (TextView) findViewById(R.id.editor_category);
        mEditorTime = (TextView) findViewById(R.id.editor_time);
        mEditorTitle = (TextView) findViewById(R.id.editor_title);
        mWordCount = (TextView) findViewById(R.id.editor_word_count);
        mPinButton = (ImageButton) findViewById(R.id.editor_pin);
    }

    private void setUpActions() {
        findViewById(R.id.editor_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.editor_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });

        mPinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPinned = !mPinned;
                updatePinButton();
                Toast.makeText(NoteEditor.this,
                        mPinned ? R.string.toast_pinned : R.string.toast_unpinned,
                        Toast.LENGTH_SHORT).show();
            }
        });

        mCategoryChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategoryDialog();
            }
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateWordCount();
            }
        };
        mNoteInput.addTextChangedListener(watcher);
        mTitleInput.addTextChangedListener(watcher);
    }

    private void loadNote() {
        if (mUri == null) {
            Toast.makeText(this, R.string.toast_note_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Cursor cursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
        if (cursor == null) {
            Toast.makeText(this, R.string.toast_note_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(this, R.string.toast_note_missing, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            mOriginalTitle = cursor.getString(1) == null ? "" : cursor.getString(1);
            mOriginalContent = cursor.getString(2) == null ? "" : cursor.getString(2);
            mCategory = NoteUtils.normalizeCategory(cursor.getString(3));
            mPinned = cursor.getInt(4) == 1;
            mCreated = cursor.getLong(5);
            mModified = cursor.getLong(6);

            mTitleInput.setText(mOriginalTitle);
            mTitleInput.setSelection(mTitleInput.getText().length());
            mNoteInput.setText(mOriginalContent);
            mOriginalCategory = mCategory;
            mOriginalPinned = mPinned;
            mLoaded = true;
        } finally {
            cursor.close();
        }

        updateCategoryChip();
        updatePinButton();
        updateMeta();
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null && text.length() > 0) {
            mNoteInput.setText(text.toString());
            mNoteInput.setSelection(mNoteInput.getText().length());
            updateWordCount();
        }
    }

    private void showCategoryDialog() {
        int checked = NoteUtils.categoryIndex(mCategory);
        new AlertDialog.Builder(this)
                .setTitle(R.string.category_dialog_title)
                .setSingleChoiceItems(NoteUtils.CATEGORIES, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mCategory = NoteUtils.CATEGORIES[which];
                                updateCategoryChip();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateCategoryChip() {
        int accent = NoteUtils.categoryColor(mCategory);
        mCategoryChip.setText(getString(R.string.category_chip, mCategory));
        mCategoryChip.setTextColor(accent);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(NoteUtils.mixWithWhite(accent, 0.11f));
        background.setCornerRadius(NoteUtils.dp(this, 8));
        mCategoryChip.setBackground(background);
        mCategoryChip.setPadding(NoteUtils.dp(this, 12), NoteUtils.dp(this, 6),
                NoteUtils.dp(this, 12), NoteUtils.dp(this, 6));
    }

    private void updatePinButton() {
        int color = mPinned ? getResources().getColor(R.color.accent)
                : getResources().getColor(R.color.white);
        mPinButton.setImageTintList(ColorStateList.valueOf(color));
        mPinButton.setAlpha(mPinned ? 1f : 0.75f);
        mPinButton.setContentDescription(getString(
                mPinned ? R.string.action_unpin : R.string.action_pin));
    }

    private void updateMeta() {
        if (mState == STATE_INSERT) {
            mEditorTitle.setText(R.string.editor_title_new);
            mEditorTime.setText(getString(R.string.editor_time_created,
                    NoteUtils.formatFullTime(mCreated > 0 ? mCreated : System.currentTimeMillis())));
        } else {
            mEditorTitle.setText(R.string.editor_title_edit);
            mEditorTime.setText(getString(R.string.editor_time_modified,
                    NoteUtils.formatFullTime(mModified)));
        }
        updateWordCount();
    }

    private void updateWordCount() {
        String text = mNoteInput.getText().toString();
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        mWordCount.setText(getString(R.string.word_count, count));
    }

    /**
     * Saves the note. This runs from {@link #onPause()} so leaving the screen never loses work.
     * A brand new note that is still empty is deleted instead of being saved as a blank row.
     */
    private void saveNote() {
        if (!mLoaded || mUri == null || mDeleted) {
            return;
        }

        String content = mNoteInput.getText().toString();
        String title = mTitleInput.getText().toString().trim();
        boolean empty = title.isEmpty() && content.trim().isEmpty();

        if (empty) {
            if (isFinishing()) {
                deleteRow();
                Toast.makeText(this, R.string.toast_empty_deleted, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (title.isEmpty()) {
            title = deriveTitle(content);
            mTitleInput.setText(title);
        }

        boolean unchanged = title.equals(mOriginalTitle)
                && content.equals(mOriginalContent)
                && mCategory.equals(mOriginalCategory)
                && mPinned == mOriginalPinned;
        if (unchanged) {
            return;
        }

        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);
        values.put(NotePad.Notes.COLUMN_NAME_PINNED, mPinned ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        getContentResolver().update(mUri, values, null, null);

        mOriginalTitle = title;
        mOriginalContent = content;
        mOriginalCategory = mCategory;
        mOriginalPinned = mPinned;
        mModified = now;
        mState = STATE_EDIT;
        mEditorTitle.setText(R.string.editor_title_edit);
        mEditorTime.setText(getString(R.string.editor_time_modified,
                NoteUtils.formatFullTime(now)));

        if (isFinishing()) {
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Builds a title from the first non-empty line of the note so the list is never
     * full of "无标题" entries.
     */
    private String deriveTitle(String content) {
        String[] lines = content.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 30) {
                    return trimmed.substring(0, 30).trim() + "…";
                }
                return trimmed;
            }
        }
        return getString(R.string.note_untitled);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_dialog_title)
                .setMessage(R.string.delete_editor_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteRow();
                        Toast.makeText(NoteEditor.this, R.string.toast_deleted,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteRow() {
        if (mUri != null) {
            getContentResolver().delete(mUri, null, null);
        }
        mUri = null;
        mDeleted = true;
        mLoaded = false;
    }

    /** Keeps the status bar icons white on the blue header. */
    private void useLightStatusBarIcons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null) {
            outState.putString(STATE_URI, mUri.toString());
            outState.putInt(STATE_MODE, mState);
        }
    }
}
