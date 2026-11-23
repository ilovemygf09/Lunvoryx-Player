package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import static tv.lunvoryx.player.ui.TvStyle.dp;

final class CategoryList extends RecyclerView {
    static final class Item {
        final String id, label, icon;
        final int count;
        Item(String id, String label, String icon, int count) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.count = count;
        }
    }

    interface Style {
        void apply(LinearLayout row, LineIcon icon, TextView label, TextView count, boolean selected, boolean focused);
    }
    interface Selection { void select(Item item); }

    private List<Item> items = Collections.emptyList();
    private int selected;
    private final Style style;
    private final Selection selection;

    CategoryList(Context context, Style style, Selection selection) {
        super(context);
        this.style = style;
        this.selection = selection;
        setLayoutManager(new LinearLayoutManager(context));
        setHasFixedSize(true);
        setItemAnimator(null);
        setVerticalScrollBarEnabled(false);
        setAdapter(new Adapter<Row>() {
            @Override public int getItemCount() { return items.size(); }
            @Override public Row onCreateViewHolder(ViewGroup parent, int type) { return new Row(context); }
            @Override public void onBindViewHolder(Row row, int position) {
                Item item = items.get(position);
                row.icon.setKind(item.icon);
                row.label.setText(item.label);
                row.count.setText(String.valueOf(item.count));
                row.updateStyle(position == selected);
            }
        });
    }

    void show(List<Item> data, String selectedId) {
        items = data;
        selected = 0;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).id.equals(selectedId)) { selected = i; break; }
        }
        getAdapter().notifyDataSetChanged();
    }

    static void focusFirstAfterLayout(RecyclerView results, View entryFocus) {
        results.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                results.getViewTreeObserver().removeOnPreDrawListener(this);
                View first = results.getChildAt(0);
                View focus = results.getRootView().findFocus();
                if (results.isAttachedToWindow() && first != null && (focus == null || focus == entryFocus))
                    first.requestFocus();
                return true;
            }
        });
    }

    private final class Row extends ViewHolder {
        final LinearLayout row;
        final LineIcon icon;
        final TextView label, count;

        Row(Context context) {
            super(new LinearLayout(context));
            row = (LinearLayout) itemView;
            row.setFocusable(true);
            row.setClickable(true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.bottomMargin = dp(context, 4);
            row.setLayoutParams(params);
            icon = new LineIcon(context, "folder");
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
            label = new TextView(context);
            label.setTextSize(12);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(context, 8), 0, dp(context, 6), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            count = new TextView(context);
            count.setTextSize(10);
            count.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF282A3A);
            bg.setCornerRadius(dp(context, 10));
            count.setBackground(bg);
            row.addView(count);
            row.setOnFocusChangeListener((v, focused) -> updateStyle(getBindingAdapterPosition() == selected));
            row.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == NO_POSITION) return;
                int previous = selected;
                selected = position;
                if (previous != position) {
                    Row old = (Row) findViewHolderForAdapterPosition(previous);
                    if (old != null) old.updateStyle(false);
                    updateStyle(true);
                }
                selection.select(items.get(position));
            });
        }

        void updateStyle(boolean active) { style.apply(row, icon, label, count, active, row.hasFocus()); }
    }
}
