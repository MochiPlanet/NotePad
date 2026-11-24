package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * The home screen of the note application.
 *
 * <p>Compared with the original NotePad sample this screen adds the two required features
 * (a timestamp on every note and a search box that looks inside titles and contents) plus
 * several optional ones: category filters, pinned notes, selectable sort orders and a
 * plain-text export of all notes.</p>
 */
public class NotesList extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int LOADER_NOTES = 1;
    private static final int REQUEST_EXPORT = 1001;

    private static final String PREFS_NAME = "notepad_prefs";
    private static final String PREF_SORT_INDEX = "sort_index";
    private static final String STATE_CATEGORY = "state_category";

    private static final String[] SORT_LABELS = new String[] {
            "最近修改优先", "最早修改优先", "标题 A → Z", "创建时间优先"
    };

    private static final String[] SORT_ORDERS = new String[] {
            NotePad.Notes.SORT_MODIFIED_DESC,
            NotePad.Notes.SORT_MODIFIED_ASC,
            NotePad.Notes.SORT_TITLE_ASC,
            NotePad.Notes.SORT_CREATED_DESC
    };

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                       // 0
            NotePad.Notes.COLUMN_NAME_TITLE,         // 1
            NotePad.Notes.COLUMN_NAME_NOTE,          // 2
            NotePad.Notes.COLUMN_NAME_CATEGORY,      // 3
            NotePad.Notes.COLUMN_NAME_PINNED,        // 4
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,   // 5
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE // 6
    };

    private ListView mListView;
    private NoteListAdapter mAdapter;
    private EditText mSearchInput;
    private ImageButton mClearSearch;
    private TextView mCountView;
    private LinearLayout mCategoryBar;
    private TextView mEmptyTitle;
    private TextView mEmptyHint;
    private View mEmptyView;

    private final List<TextView> mCategoryChips = new ArrayList<>();

    private String mQuery = "";
    private String mCategory;        // null means "全部"
    private int mSortIndex;
    private String mSelection;
    private String[] mSelectionArgs;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mSearchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);

        getWindow().setStatusBarColor(getResources().getColor(R.color.primary));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.primary_dark));
        useLightStatusBarIcons();

        mListView = (ListView) findViewById(R.id.notes_list);
        mSearchInput = (EditText) findViewById(R.id.search_input);
        mClearSearch = (ImageButton) findViewById(R.id.search_clear);
        mCountView = (TextView) findViewById(R.id.note_count);
        mCategoryBar = (LinearLayout) findViewById(R.id.category_bar);
        mEmptyView = findViewById(R.id.empty_state);
        mEmptyTitle = (TextView) findViewById(R.id.empty_title);
        mEmptyHint = (TextView) findViewById(R.id.empty_hint);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mSortIndex = prefs.getInt(PREF_SORT_INDEX, 0);
        if (mSortIndex < 0 || mSortIndex >= SORT_ORDERS.length) {
            mSortIndex = 0;
        }
        if (savedInstanceState != null) {
            mCategory = savedInstanceState.getString(STATE_CATEGORY);
        }

        mAdapter = new NoteListAdapter(this, null);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView);

        // Do not let the search box grab focus (and pop up the keyboard) on launch.
        findViewById(R.id.notes_root).requestFocus();
        mSearchInput.clearFocus();

        buildCategoryBar();
        setUpSearch();
        setUpListActions();
        setUpHeaderActions();
        watchKeyboard();

        getLoaderManager().initLoader(LOADER_NOTES, null, this);
    }

    /**
     * Hides the floating "new note" button while the keyboard is open. Otherwise the button
     * can sit on top of the last visible note card in the shortened window.
     */
    private void watchKeyboard() {
        final View root = findViewById(R.id.notes_root);
        final View fab = findViewById(R.id.fab);
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        Rect visibleFrame = new Rect();
                        root.getWindowVisibleDisplayFrame(visibleFrame);
                        int hiddenHeight = root.getRootView().getHeight() - visibleFrame.height();
                        boolean keyboardVisible = hiddenHeight > NoteUtils.dp(NotesList.this, 150);
                        fab.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the editor can leave the IME open; keep the list unobstructed.
        mSearchInput.clearFocus();
        findViewById(R.id.notes_root).requestFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------------------------------
    // Header: search, categories, sort and export
    // ------------------------------------------------------------------------------------

    private void setUpSearch() {
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mClearSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                mQuery = s.toString().trim();
                scheduleReload();
            }
        });

        mClearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchInput.setText("");
                mSearchInput.requestFocus();
            }
        });

        mSearchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    reloadNotes();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Waits a moment after the user stops typing before querying the provider again, so
     * a fast typist does not trigger one database query per character.
     */
    private void scheduleReload() {
        if (mSearchRunnable != null) {
            mHandler.removeCallbacks(mSearchRunnable);
        }
        mSearchRunnable = new Runnable() {
            @Override
            public void run() {
                reloadNotes();
            }
        };
        mHandler.postDelayed(mSearchRunnable, 220);
    }

    private void buildCategoryBar() {
        addCategoryChip(NoteUtils.ALL_CATEGORIES, null);

        for (String category : NoteUtils.CATEGORIES) {
            addCategoryChip(category, category);
        }
    }

    private void addCategoryChip(String label, final String categoryValue) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(NoteUtils.dp(this, 15), 0, NoteUtils.dp(this, 15), 0);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setTextColor(getResources().getColorStateList(R.color.chip_text));
        boolean isAllChip = categoryValue == null;
        chip.setSelected(isAllChip ? mCategory == null : categoryValue.equals(mCategory));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, NoteUtils.dp(this, 34));
        params.setMarginEnd(NoteUtils.dp(this, 8));
        chip.setLayoutParams(params);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCategory(categoryValue);
            }
        });

        mCategoryBar.addView(chip);
        mCategoryChips.add(chip);
    }

    private void selectCategory(String categoryValue) {
        mCategory = categoryValue;
        for (int i = 0; i < mCategoryChips.size(); i++) {
            TextView chip = mCategoryChips.get(i);
            boolean chipIsAll = i == 0;
            chip.setSelected(chipIsAll ? categoryValue == null
                    : categoryValue != null && chip.getText().toString().equals(categoryValue));
        }
        updateEmptyStateText();
        reloadNotes();
    }

    private void setUpHeaderActions() {
        findViewById(R.id.btn_sort).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortDialog();
            }
        });

        findViewById(R.id.btn_export).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportAllNotes();
            }
        });

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NotesList.this, NoteEditor.class);
                intent.setAction(Intent.ACTION_INSERT);
                intent.setData(NotePad.Notes.CONTENT_URI);
                intent.putExtra(NoteEditor.EXTRA_CATEGORY,
                        mCategory == null ? NotePad.Notes.CATEGORY_DEFAULT : mCategory);
                startActivity(intent);
            }
        });
    }

    private void showSortDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(SORT_LABELS, mSortIndex,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                mSortIndex = which;
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit()
                                        .putInt(PREF_SORT_INDEX, which)
                                        .apply();
                                dialog.dismiss();
                                reloadNotes();
                                Toast.makeText(NotesList.this,
                                        getString(R.string.sort_changed, SORT_LABELS[which]),
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------------------------
    // List interactions
    // ------------------------------------------------------------------------------------

    private void setUpListActions() {
        mListView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                openNote(id);
            }
        });

        mListView.setOnItemLongClickListener(
                new android.widget.AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(android.widget.AdapterView<?> parent,
                                                   View view, int position, long id) {
                        showNoteActions(id);
                        return true;
                    }
                });
    }

    private void openNote(long id) {
        Uri uri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
        Intent intent = new Intent(this, NoteEditor.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.setData(uri);
        startActivity(intent);
    }

    private void showNoteActions(final long id) {
        final Uri uri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
        String title = getString(R.string.note_untitled);
        String content = "";
        boolean pinned = false;

        Cursor cursor = getContentResolver().query(uri, PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    title = cursor.getString(1);
                    content = cursor.getString(2);
                    pinned = cursor.getInt(4) == 1;
                    if (TextUtils.isEmpty(title)) {
                        title = getString(R.string.note_untitled);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        final String noteTitle = title;
        final String noteContent = content;
        final boolean wasPinned = pinned;
        final String[] actions = new String[] {
                getString(R.string.action_open),
                getString(pinned ? R.string.action_unpin : R.string.action_pin),
                getString(R.string.action_share),
                getString(R.string.action_delete)
        };

        new AlertDialog.Builder(this)
                .setTitle(noteTitle)
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            openNote(id);
                        } else if (which == 1) {
                            togglePin(uri, wasPinned);
                        } else if (which == 2) {
                            shareNote(noteTitle, noteContent);
                        } else if (which == 3) {
                            confirmDelete(uri, noteTitle);
                        }
                    }
                })
                .show();
    }

    private void togglePin(Uri uri, boolean wasPinned) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_PINNED, wasPinned ? 0 : 1);
        getContentResolver().update(uri, values, null, null);
        Toast.makeText(this, wasPinned ? R.string.toast_unpinned : R.string.toast_pinned,
                Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(final Uri uri, String title) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_dialog_title)
                .setMessage(getString(R.string.delete_dialog_message, title))
                .setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        getContentResolver().delete(uri, null, null);
                        Toast.makeText(NotesList.this, R.string.toast_deleted,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareNote(String title, String content) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, title + "\n\n" + content);
        startActivity(Intent.createChooser(send, getString(R.string.share_note)));
    }

    // ------------------------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------------------------

    private void exportAllNotes() {
        if (mAdapter == null || mAdapter.getCount() == 0) {
            Toast.makeText(this, R.string.toast_nothing_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,
                "NotePad_" + NoteUtils.formatFileStamp(System.currentTimeMillis()) + ".txt");

        try {
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_file_manager, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        writeExportFile(data.getData());
    }

    private void writeExportFile(Uri target) {
        Cursor cursor = null;
        Writer writer = null;
        try {
            cursor = getContentResolver().query(NotePad.Notes.CONTENT_URI, PROJECTION,
                    null, null, NotePad.Notes.SORT_MODIFIED_DESC);
            if (cursor == null) {
                throw new IOException("query failed");
            }

            OutputStream output = getContentResolver().openOutputStream(target);
            if (output == null) {
                throw new IOException("cannot open target");
            }
            writer = new OutputStreamWriter(output, "UTF-8");

            // UTF-8 BOM so the exported file also opens correctly in Windows Notepad.
            writer.write('\uFEFF');

            int total = cursor.getCount();
            StringBuilder header = new StringBuilder();
            header.append(getString(R.string.app_name)).append(" 笔记备份\r\n");
            header.append("导出时间：")
                    .append(NoteUtils.formatFullTime(System.currentTimeMillis()))
                    .append("\r\n");
            header.append("共 ").append(total).append(" 条笔记\r\n");
            header.append("========================================\r\n\r\n");
            writer.write(header.toString());

            int index = 0;
            while (cursor.moveToNext()) {
                index++;
                String title = cursor.getString(1);
                String content = cursor.getString(2);
                String category = NoteUtils.normalizeCategory(cursor.getString(3));
                boolean pinned = cursor.getInt(4) == 1;
                long created = cursor.getLong(5);
                long modified = cursor.getLong(6);
                if (TextUtils.isEmpty(title)) {
                    title = getString(R.string.note_untitled);
                }

                StringBuilder entry = new StringBuilder();
                entry.append(index).append(". ");
                if (pinned) {
                    entry.append("[置顶] ");
                }
                entry.append("【").append(category).append("】").append(title).append("\r\n");
                entry.append("创建：").append(NoteUtils.formatFullTime(created))
                        .append("    修改：").append(NoteUtils.formatFullTime(modified))
                        .append("\r\n");
                entry.append("----------------------------------------\r\n");
                entry.append(content == null ? "" : content).append("\r\n\r\n");
                writer.write(entry.toString());
            }
            writer.flush();

            Toast.makeText(this, getString(R.string.toast_exported, total),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_export_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Loader
    // ------------------------------------------------------------------------------------

    private void reloadNotes() {
        if (mSearchRunnable != null) {
            mHandler.removeCallbacks(mSearchRunnable);
        }
        buildSelection();
        if (mAdapter != null) {
            mAdapter.setQuery(mQuery);
        }
        getLoaderManager().restartLoader(LOADER_NOTES, null, this);
    }

    /**
     * Builds the SQL selection for the current search text and category. Search covers both
     * the title and the note content; wildcards typed by the user are escaped.
     */
    private void buildSelection() {
        List<String> args = new ArrayList<>();
        String selection = null;

        if (!TextUtils.isEmpty(mQuery)) {
            String like = "%" + NoteUtils.escapeLike(mQuery) + "%";
            selection = "(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? ESCAPE '\\'"
                    + " OR " + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ? ESCAPE '\\')";
            args.add(like);
            args.add(like);
        }

        if (mCategory != null) {
            String categorySelection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
            selection = selection == null ? categorySelection
                    : selection + " AND " + categorySelection;
            args.add(mCategory);
        }

        mSelection = selection;
        mSelectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, NotePad.Notes.CONTENT_URI, PROJECTION,
                mSelection, mSelectionArgs, SORT_ORDERS[mSortIndex]);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        updateCount(data);
        updateEmptyStateText();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    private void updateCount(Cursor cursor) {
        int count = cursor == null ? 0 : cursor.getCount();
        if (!TextUtils.isEmpty(mQuery) || mCategory != null) {
            mCountView.setText(getString(R.string.count_filtered, count));
        } else {
            mCountView.setText(getString(R.string.count_all, count));
        }
    }

    private void updateEmptyStateText() {
        if (!TextUtils.isEmpty(mQuery)) {
            mEmptyTitle.setText(R.string.empty_search_title);
            mEmptyHint.setText(R.string.empty_search_hint);
        } else if (mCategory != null) {
            mEmptyTitle.setText(getString(R.string.empty_category_title, mCategory));
            mEmptyHint.setText(R.string.empty_category_hint);
        } else {
            mEmptyTitle.setText(R.string.empty_notes_title);
            mEmptyHint.setText(R.string.empty_notes_hint);
        }
    }

    // ------------------------------------------------------------------------------------
    // Small utilities
    // ------------------------------------------------------------------------------------

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CATEGORY, mCategory);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSearchRunnable != null) {
            mHandler.removeCallbacks(mSearchRunnable);
        }
    }
}
