package me.saket.dank.ui.subreddits;

import static me.saket.dank.utils.RxUtils.applySchedulers;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.dean.jraw.paginators.Paginator;

import java.util.concurrent.TimeUnit;

import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import me.saket.dank.R;
import me.saket.dank.di.Dank;
import me.saket.dank.widgets.ToolbarExpandableSheet;
import rx.Observable;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

public class UserProfileSheetView extends FrameLayout {

    @BindView(R.id.userprofilesheet_karma) TextView karmaView;
    @BindView(R.id.userprofilesheet_messages) TextView messagesView;

    @BindColor(R.color.userprofile_no_messages) int noMessagesTextColor;
    @BindColor(R.color.userprofile_unread_messages) int unreadMessagesTextColor;

    private ToolbarExpandableSheet parentSheet;
    private Subscription confirmLogoutTimer = Subscriptions.unsubscribed();
    private Subscription logoutSubscription = Subscriptions.empty();
    private Subscription userInfoSubscription = Subscriptions.empty();

    public static UserProfileSheetView showIn(ToolbarExpandableSheet toolbarSheet) {
        UserProfileSheetView subredditPickerView = new UserProfileSheetView(toolbarSheet.getContext());
        toolbarSheet.addView(subredditPickerView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subredditPickerView.setParentSheet(toolbarSheet);
        return subredditPickerView;
    }

    public UserProfileSheetView(Context context) {
        super(context);
        inflate(context, R.layout.view_user_profile_sheet, this);
        ButterKnife.bind(this, this);

        // TODO: 02/03/17 Cache user account.

        karmaView.setText(R.string.loading_karma);

        userInfoSubscription = Dank.reddit().authenticateIfNeeded()
                .flatMap(__ -> Dank.reddit().loggedInUserAccount())
                .retryWhen(Dank.reddit().refreshApiTokenAndRetryIfExpired())
                .compose(applySchedulers())
                .subscribe(loggedInUser -> {
                    // Populate karma.
                    Integer commentKarma = loggedInUser.getCommentKarma();
                    Integer linkKarma = loggedInUser.getLinkKarma();
                    int karmaCount = commentKarma + linkKarma;

                    String compactKarma;
                    if (karmaCount < 1_000) {
                        compactKarma = String.valueOf(karmaCount);
                    } else if (karmaCount < 1_000_000) {
                        compactKarma = karmaCount / 1_000 + "k";
                    } else {
                        compactKarma = karmaCount / 1_000_000 + "m";
                    }
                    karmaView.setText(getResources().getString(R.string.karma_count, compactKarma));

                    // Populate message count.
                    Integer inboxCount = loggedInUser.getInboxCount();
                    if (inboxCount == 0) {
                        messagesView.setText(R.string.messages);

                    } else if (inboxCount == 1) {
                        messagesView.setText(getResources().getString(R.string.unread_messages_count_single, (int) inboxCount));

                    } else if (inboxCount < Paginator.RECOMMENDED_MAX_LIMIT) {
                        messagesView.setText(getResources().getString(R.string.unread_messages_count_99_or_less, (int) inboxCount));

                    } else {
                        messagesView.setText(R.string.unread_messages_count_99_plus);
                    }
                    messagesView.setTextColor(inboxCount > 0 ? unreadMessagesTextColor : noMessagesTextColor);

                }, error -> {
                    Timber.e(error, "Couldn't get logged in user info");
                    karmaView.setText(R.string.error_user_karma_load);
                });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        logoutSubscription.unsubscribe();
        userInfoSubscription.unsubscribe();
    }

    public void setParentSheet(ToolbarExpandableSheet parentSheet) {
        this.parentSheet = parentSheet;
    }

    @OnClick(R.id.userprofilesheet_messages)
    void onClickMessages() {
        // TODO: 02/03/17
        parentSheet.collapse();
    }

    @OnClick(R.id.userprofilesheet_comments)
    void onClickComments() {
        // TODO: 02/03/17
        parentSheet.collapse();
    }

    @OnClick(R.id.userprofilesheet_submissions)
    void onClickSubmissions() {
        // TODO: 02/03/17
        parentSheet.collapse();
    }

    @OnClick(R.id.userprofilesheet_logout)
    void onClickLogout(TextView logoutButton) {
        if (confirmLogoutTimer.isUnsubscribed()) {
            logoutButton.setText(R.string.confirm_logout);
            confirmLogoutTimer = Observable.timer(5, TimeUnit.SECONDS)
                    .compose(applySchedulers())
                    .subscribe(__ -> {
                        logoutButton.setText(R.string.logout);
                    });

        } else {
            // Confirm logout was visible when this button was clicked. Logout the user for real.
            confirmLogoutTimer.unsubscribe();
            logoutSubscription.unsubscribe();
            logoutButton.setText(R.string.logging_out);

            logoutSubscription = Dank.reddit()
                    .logout()
                    .compose(applySchedulers())
                    .subscribe(__ -> {
                        parentSheet.collapse();

                    }, error -> {
                        logoutButton.setText(R.string.logout);
                        Timber.e(error, "Logout failure");
                    });
        }
    }

}
