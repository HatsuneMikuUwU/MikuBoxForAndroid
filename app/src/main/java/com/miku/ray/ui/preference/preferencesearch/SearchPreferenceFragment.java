package com.miku.ray.ui.preference.preferencesearch;

import com.miku.ray.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.miku.ray.ui.preference.preferencesearch.AnimationUtils;
import com.miku.ray.ui.preference.preferencesearch.RevealAnimationSetting;

import java.util.ArrayList;
import java.util.List;

public class SearchPreferenceFragment extends Fragment implements SearchPreferenceAdapter.SearchClickListener {
    public static final String TAG = "SearchPreferenceFragment";

    private static final String SHARED_PREFS_FILE = "preferenceSearch";
    private static final int MAX_HISTORY = 5;
    private PreferenceParser searcher;
    private List<PreferenceItem> results;
    private List<HistoryItem> history;
    private SharedPreferences prefs;
    private SearchViewHolder viewHolder;
    private SearchConfiguration searchConfiguration;
    private SearchPreferenceAdapter adapter;
    private HistoryClickListener historyClickListener;
    private CharSequence searchTermPreset = null;
    /** True between a non-empty query and the next empty one; drives the auto-exit on clear. */
    private boolean hadActiveQuery = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getContext().getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE);
        searcher = new PreferenceParser(getContext());
        if (getActivity() instanceof SummaryResolver) {
            // The parser needs stored preference values to expand a
            // ListPreference's "%s" summary into the current entry label.
            searcher.setSummaryResolver((SummaryResolver) getActivity());
        }

        searchConfiguration = SearchConfiguration.fromBundle(getArguments());
        ArrayList<SearchConfiguration.SearchIndexItem> files = searchConfiguration.getFiles();
        for (SearchConfiguration.SearchIndexItem file : files) {
            searcher.addResourceFile(file);
        }
        searcher.addPreferenceItems(searchConfiguration.getPreferencesToIndex());
        loadHistory();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.searchpreference_fragment, container, false);
        viewHolder = new SearchViewHolder(rootView);

        viewHolder.clearButton.setOnClickListener(view -> viewHolder.searchView.setText(""));
        if (searchConfiguration.isHistoryEnabled()) {
            viewHolder.moreButton.setVisibility(View.VISIBLE);
        }
        if (searchConfiguration.getTextHint() != null) {
            viewHolder.searchView.setHint(searchConfiguration.getTextHint());
        }
        if (searchConfiguration.getTextNoResults() != null) {
            viewHolder.noResults.setText(searchConfiguration.getTextNoResults());
        }
        if (searchConfiguration.getTextClearInput() != null) {
            viewHolder.clearButton.setContentDescription(searchConfiguration.getTextClearInput());
        }
        if (searchConfiguration.getTextMore() != null) {
            viewHolder.moreButton.setContentDescription(searchConfiguration.getTextMore());
        }
        viewHolder.moreButton.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(getContext(), viewHolder.moreButton);
                popup.getMenuInflater().inflate(R.menu.searchpreference_more, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == R.id.clear_history) {
                            clearHistory();
                        }
                        return true;
                });
                if (searchConfiguration.getTextClearHistory() != null) {
                    popup.getMenu().findItem(R.id.clear_history).setTitle(searchConfiguration.getTextClearHistory());
                }
                popup.show();
        });

        viewHolder.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SearchPreferenceAdapter();
        adapter.setSearchConfiguration(searchConfiguration);
        adapter.setOnItemClickListener(this);
        viewHolder.recyclerView.setAdapter(adapter);

        viewHolder.searchView.addTextChangedListener(textWatcher);
        viewHolder.searchView.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    return true;
                }
                return false;
        });

        if (!searchConfiguration.isSearchBarEnabled()) {
            viewHolder.cardView.setVisibility(View.GONE);
        }

        if (searchTermPreset != null) {
            viewHolder.searchView.setText(searchTermPreset);
        }

        RevealAnimationSetting anim = searchConfiguration.getRevealAnimationSetting();
        if (anim != null) {
            AnimationUtils.registerCircularRevealAnimation(getContext(), rootView, anim);
        }
        rootView.setOnTouchListener((v, event) -> true);
        return rootView;
    }

    @Override
    public void onViewCreated(android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                androidx.core.graphics.Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                int topInset = searchConfiguration.isSearchBarEnabled()
                ? Math.max(systemBars.top, cutout.top)
                : 0;
                int bottomInset = Math.max(systemBars.bottom, cutout.bottom);
                v.setPadding(
                    v.getPaddingLeft(),
                    topInset,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
                );

                int baseBottomPadding = (int) (16 * v.getResources().getDisplayMetrics().density);
                viewHolder.recyclerView.setPadding(
                    viewHolder.recyclerView.getPaddingLeft(),
                    viewHolder.recyclerView.getPaddingTop(),
                    viewHolder.recyclerView.getPaddingRight(),
                    baseBottomPadding + bottomInset
                );
                return insets;
        });
    }

    private void loadHistory() {
        history = new ArrayList<>();
        if (!searchConfiguration.isHistoryEnabled()) {
            return;
        }

        int size = prefs.getInt(historySizeKey(), 0);
        for (int i = 0; i < size; i++) {
            String title = prefs.getString(historyEntryKey(i), null);
            history.add(new HistoryItem(title));
        }
    }

    private void saveHistory() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(historySizeKey(), history.size());
        for (int i = 0; i < history.size(); i++) {
            editor.putString(historyEntryKey(i), history.get(i).getTerm());
        }
        editor.apply();
    }

    private String historySizeKey() {
        if (searchConfiguration.getHistoryId() != null) {
            return searchConfiguration.getHistoryId() + "_history_size";
        } else {
            return "history_size";
        }
    }

    private String historyEntryKey(int i) {
        if (searchConfiguration.getHistoryId() != null) {
            return searchConfiguration.getHistoryId() + "_history_" + i;
        } else {
            return "history_" + i;
        }
    }

    public boolean hasHistory() {
        return history != null && !history.isEmpty();
    }

    public void clearHistory() {
        viewHolder.searchView.setText("");
        history.clear();
        saveHistory();
        updateSearchResults("");
    }

    private void addHistoryEntry(String entry) {
        HistoryItem newItem = new HistoryItem(entry);
        if (!history.contains(newItem)) {
            if (history.size() >= MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
            history.add(0, newItem);
            saveHistory();
            updateSearchResults(viewHolder.searchView.getText().toString());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSearchResults(viewHolder.searchView.getText().toString());

        if (searchConfiguration.isSearchBarEnabled()) {
            showKeyboard();
        }
    }

    private void showKeyboard() {
        viewHolder.searchView.post(() -> {
                viewHolder.searchView.requestFocus();
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(viewHolder.searchView, InputMethodManager.SHOW_IMPLICIT);
                }
        });
    }

    private void hideKeyboard() {
        View view = getActivity().getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (view != null && imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    public void setSearchTerm(CharSequence term) {
        if (viewHolder != null) {
            viewHolder.searchView.setText(term);
        } else {
            searchTermPreset = term;
        }
    }

    private void updateSearchResults(String keyword) {
        adapter.setKeyword(keyword);

        if (TextUtils.isEmpty(keyword)) {
            // A query the user cleared must not park the screen on the "no
            // results" state (previously only BACK recovered it): leave the
            // search when there is no history to fall back to. The guard keeps
            // the very first, still-empty pass from dismissing itself.
            boolean wasSearching = hadActiveQuery;
            hadActiveQuery = false;
            if (wasSearching && !hasValidHistory()) {
                exitSearch();
                return;
            }
            showHistory();
            return;
        }

        hadActiveQuery = true;
        results = searcher.searchFor(keyword);
        adapter.setContent(new ArrayList<>(results));

        setEmptyViewShown(results.isEmpty());
    }

    private boolean hasValidHistory() {
        for (HistoryItem item : history) {
            if (!searcher.searchFor(item.getTerm()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Leaves the search state, restoring the settings screen underneath. */
    private void exitSearch() {
        androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity == null || !isAdded()) {
            return;
        }
        androidx.fragment.app.FragmentManager fm = activity.getSupportFragmentManager();
        fm.beginTransaction().remove(this).commit();
        fm.popBackStack(TAG, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void setEmptyViewShown(boolean shown) {
        if (shown) {
            viewHolder.noResults.setVisibility(View.VISIBLE);
            viewHolder.recyclerView.setVisibility(View.GONE);
        } else {
            viewHolder.noResults.setVisibility(View.GONE);
            viewHolder.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showHistory() {
        viewHolder.noResults.setVisibility(View.GONE);
        viewHolder.recyclerView.setVisibility(View.VISIBLE);

        List<HistoryItem> validHistory = new ArrayList<>();
        for (HistoryItem item : history) {
            List<PreferenceItem> itemResults = searcher.searchFor(item.getTerm());
            if (!itemResults.isEmpty()) {
                validHistory.add(item);
            }
        }

        adapter.setContent(new ArrayList<>(validHistory));
        setEmptyViewShown(validHistory.isEmpty());
    }

    @Override
    public void onItemClicked(ListItem item, int position) {
        if (item.getType() == HistoryItem.TYPE) {
            CharSequence text = ((HistoryItem) item).getTerm();
            viewHolder.searchView.setText(text);
            viewHolder.searchView.setSelection(text.length());
            if (historyClickListener != null) {
                historyClickListener.onHistoryEntryClicked(text.toString());
            }
        } else {
            hideKeyboard();

            try {
                final SearchPreferenceResultListener callback = (SearchPreferenceResultListener) getActivity();
                PreferenceItem r = results.get(position);
                if (r.title != null) {
                    addHistoryEntry(r.title);
                }
                String screen = null;
                if (!r.keyBreadcrumbs.isEmpty()) {
                    screen = r.keyBreadcrumbs.get(r.keyBreadcrumbs.size() - 1);
                }
                SearchPreferenceResult result = new SearchPreferenceResult(r.key, r.resId, screen);
                callback.onSearchResultClicked(result);
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity().toString() + " must implement SearchPreferenceResultListener");
            }
        }
    }

    private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            updateSearchResults(editable.toString());
            viewHolder.clearButton.setVisibility(editable.toString().isEmpty() ? View.GONE : View.VISIBLE);
        }
    };

    public void setHistoryClickListener(HistoryClickListener historyClickListener) {
        this.historyClickListener = historyClickListener;
    }

    private static class SearchViewHolder {
        private ImageView clearButton;
        private ImageView moreButton;
        private EditText searchView;
        private RecyclerView recyclerView;
        private TextView noResults;
        private CardView cardView;

        SearchViewHolder(View root) {
            searchView = root.findViewById(R.id.search);
            clearButton = root.findViewById(R.id.clear);
            recyclerView = root.findViewById(R.id.list);
            moreButton = root.findViewById(R.id.more);
            noResults = root.findViewById(R.id.no_results);
            cardView = root.findViewById(R.id.search_card);
        }
    }

    public interface HistoryClickListener {
        void onHistoryEntryClicked(String entry);
    }
}
