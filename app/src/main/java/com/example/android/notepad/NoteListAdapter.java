package com.example.android.notepad;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Turns a cursor of notes into the cards shown by {@link NotesList}.
 * Every card carries a category color, a title, a content preview, a timestamp and
 * a pin marker, so the list answers "what / when" at a glance.
 */
public class NoteListAdapter extends CursorAdapter {

    private final LayoutInflater mInflater;
    private String mQuery = "";

    public NoteListAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
        mInflater = LayoutInflater.from(context);
    }

    /** Sets the current search text so matches can be highlighted inside each row. */
    public void setQuery(String query) {
        mQuery = query == null ? "" : query.trim();
        notifyDataSetChanged();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.noteslist_item, parent, false);
        ViewHolder holder = new ViewHolder(view);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder) view.getTag();

        String title = cursor.getString(cursor.getColumnIndexOrThrow(
                NotePad.Notes.COLUMN_NAME_TITLE));
        String content = cursor.getString(cursor.getColumnIndexOrThrow(
                NotePad.Notes.COLUMN_NAME_NOTE));
        String category = NoteUtils.normalizeCategory(cursor.getString(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_CATEGORY)));
        long modified = cursor.getLong(cursor.getColumnIndexOrThrow(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
        boolean pinned = cursor.getInt(cursor.getColumnIndexOrThrow(
                NotePad.Notes.COLUMN_NAME_PINNED)) == 1;

        if (TextUtils.isEmpty(title) || title.trim().isEmpty()) {
            title = context.getString(R.string.note_untitled);
        }
        String preview = NoteUtils.snippet(content);
        if (TextUtils.isEmpty(preview)) {
            preview = context.getString(R.string.note_empty_preview);
        }

        int accent = NoteUtils.categoryColor(category);

        holder.title.setText(highlight(title, mQuery));
        holder.preview.setText(highlight(preview, mQuery));
        holder.time.setText(NoteUtils.formatListTime(modified));

        holder.category.setText(category);
        holder.category.setTextColor(accent);
        holder.category.setBackground(roundedBackground(
                NoteUtils.mixWithWhite(accent, 0.13f), NoteUtils.dp(context, 6)));

        // A very light category tint keeps the list colorful without hurting readability.
        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setColor(NoteUtils.mixWithWhite(accent, 0.055f));
        card.setCornerRadius(NoteUtils.dp(context, 18));
        card.setStroke(NoteUtils.dp(context, 1), NoteUtils.mixWithWhite(accent, 0.16f));
        holder.card.setBackground(card);
        holder.card.setClipToOutline(true);

        holder.accentBar.setBackgroundColor(accent);

        holder.pin.setVisibility(pinned ? View.VISIBLE : View.GONE);
        holder.pin.setImageTintList(ColorStateList.valueOf(accent));
    }

    private static GradientDrawable roundedBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    /** Highlights every occurrence of the search text with a soft yellow marker. */
    private static CharSequence highlight(String text, String query) {
        if (TextUtils.isEmpty(query) || TextUtils.isEmpty(text)) {
            return text;
        }
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        SpannableString spannable = new SpannableString(text);
        int start = 0;
        boolean found = false;
        while (true) {
            int index = lowerText.indexOf(lowerQuery, start);
            if (index < 0) {
                break;
            }
            spannable.setSpan(
                    new BackgroundColorSpan(0x55FFC107),
                    index,
                    index + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = index + lowerQuery.length();
            found = true;
        }
        return found ? spannable : text;
    }

    private static final class ViewHolder {
        final View card;
        final View accentBar;
        final TextView title;
        final TextView preview;
        final TextView category;
        final TextView time;
        final ImageView pin;

        ViewHolder(View root) {
            card = root.findViewById(R.id.note_card);
            accentBar = root.findViewById(R.id.note_accent);
            title = root.findViewById(R.id.note_title);
            preview = root.findViewById(R.id.note_preview);
            category = root.findViewById(R.id.note_category);
            time = root.findViewById(R.id.note_time);
            pin = root.findViewById(R.id.note_pin);
        }
    }
}
