package com.escape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.escape.app.R;
import com.escape.app.model.AppInfo;
import com.escape.app.model.AppRestriction;
import com.escape.app.utils.PreferencesManager;

import java.util.List;

/** prikaz i odabir aplikacija za ograničavanje (Stevo gledaj nove boje za komentare ahaha)*/
public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private final List<AppInfo> appList;
    private final PreferencesManager preferencesManager;
    private final OnAppClickListener listener;

    public interface OnAppClickListener {
        void onAppClicked(AppInfo app);
    }

    public AppListAdapter(List<AppInfo> appList, PreferencesManager preferencesManager,
                         OnAppClickListener listener) {
        this.appList = appList;
        this.preferencesManager = preferencesManager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_app_list, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    /** ViewHolder za stvari liste aplikacija*/
    class AppViewHolder extends RecyclerView.ViewHolder {
        private final ImageView appIcon;
        private final TextView appName;
        private final TextView limitInfo;
        private final View iconGlow;
        private final LinearLayout timeBadge;
        private final TextView timeBadgeText;
        private final ImageView addLimitButton;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            limitInfo = itemView.findViewById(R.id.limitInfo);
            iconGlow = itemView.findViewById(R.id.iconGlow);
            timeBadge = itemView.findViewById(R.id.timeBadge);
            timeBadgeText = itemView.findViewById(R.id.timeBadgeText);
            addLimitButton = itemView.findViewById(R.id.addLimitButton);
        }

        public void bind(AppInfo app) {
            appIcon.setImageDrawable(app.getIcon());
            appName.setText(app.getAppName());

            // Provjerava da li aplikacija ima ograničenje i prikazuje informacije o limitu
            AppRestriction restriction = preferencesManager.getAppRestriction(app.getPackageName());
            if (restriction != null && restriction.isEnabled() && restriction.getDailyLimitMinutes() > 0) {
                int totalMinutes = restriction.getDailyLimitMinutes();
                int hours = totalMinutes / 60;
                int minutes = totalMinutes % 60;
                
                // Prikazuje oznaku vremena
                timeBadge.setVisibility(View.VISIBLE);
                addLimitButton.setVisibility(View.GONE);
                iconGlow.setVisibility(View.VISIBLE);
                
                // Formatira prikaz vremena
                if (hours > 0 && minutes > 0) {
                    timeBadgeText.setText(hours + "h " + minutes + "m");
                } else if (hours > 0) {
                    timeBadgeText.setText(hours + "h");
                } else {
                    timeBadgeText.setText(minutes + "m");
                }
                
                limitInfo.setText(itemView.getContext().getString(R.string.limit_value, totalMinutes));
                limitInfo.setTextColor(0xFF64B5F6); // Blue color for active limits
            } else {
                // Prikazuje dugme za dodavanje
                timeBadge.setVisibility(View.GONE);
                addLimitButton.setVisibility(View.VISIBLE);
                iconGlow.setVisibility(View.GONE);
                
                limitInfo.setText(R.string.set_limit_prompt);
                limitInfo.setTextColor(0xFF8899AA); // Siva, mada zapisao sam već
            }

            // Postavlja osluškivač klika na cijelu stavku
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppClicked(app);
                }
            });
        }
    }
}
