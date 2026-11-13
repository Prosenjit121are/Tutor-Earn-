package com.example;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.databinding.ActivitySurveyRewardsBinding;

import java.util.UUID;

/**
 * Survey Rewards screen featuring exactly one reward card:
 * - Title: Survey Rewards
 * - Sentence: "Watch a rewarded advertisement and claim your 500 coins!"
 * - Button: CLAIM
 * Credits 500 coins into the centralized Tutor Earn balance (users/{uid}/coins/balance)
 * strictly after official AdMob RewardedAd.OnUserEarnedRewardListener confirmation.
 */
public class SurveyRewardsActivity extends AppCompatActivity {

    public static final int REWARD_COINS = 500;

    private ActivitySurveyRewardsBinding binding;
    private SurveyRewardsAdManager adManager;
    private CoinBalanceRepository coinBalanceRepository;

    private boolean isClaimFlowBusy = false;
    private AnimatorSet ambientAnimatorSet;

    private int basePaddingHorizontal;
    private int basePaddingTop;
    private int basePaddingBottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivitySurveyRewardsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        basePaddingHorizontal = dpToPx(20);
        basePaddingTop = dpToPx(14);
        basePaddingBottom = dpToPx(28);

        applySystemBarInsets();
        setupDependencies();
        setupVisualPolish();
        setupInteractions();
        setupBackNavigation();

        adManager.initializeAndPreload();
        animateEntrance();
        startAmbientAnimations();
    }

    private void setupDependencies() {
        adManager = new SurveyRewardsAdManager(this);
        coinBalanceRepository = CoinBalanceRepository.getInstance(this);
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.surveyRewardsContentWrapper, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    basePaddingHorizontal + insets.left,
                    basePaddingTop + insets.top,
                    basePaddingHorizontal + insets.right,
                    basePaddingBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupVisualPolish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int glowColor = ContextCompat.getColor(this, R.color.survey_rewards_glow);
            binding.cardSurveyReward.setOutlineSpotShadowColor(glowColor);
            binding.cardSurveyReward.setOutlineAmbientShadowColor(glowColor);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInteractions() {
        binding.btnSurveyRewardsBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        binding.btnClaimSurveyReward.setOnTouchListener((view, event) -> {
            if (!view.isEnabled()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(100L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180L)
                            .setInterpolator(new OvershootInterpolator(1.6f))
                            .start();
                    break;
            }
            return false;
        });

        binding.btnClaimSurveyReward.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClaimClicked();
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void onClaimClicked() {
        if (isClaimFlowBusy || isFinishing() || isDestroyed()) {
            return;
        }
        isClaimFlowBusy = true;
        hideStatusMessage();
        setClaimButtonLoading(true);

        final String transactionId = "sr_" + UUID.randomUUID().toString().replace("-", "");
        adManager.loadAndShowRewardedAd(this, new SurveyRewardsAdManager.RewardedAdOutcomeListener() {
            @Override
            public void onLoadingStarted() {
                runOnUiThread(() -> {
                    if (binding != null) {
                        setClaimButtonLoading(true);
                    }
                });
            }

            @Override
            public void onRewardVerified() {
                coinBalanceRepository.creditVerifiedEarning(
                        CoinEarningSource.SURVEY_REWARDS,
                        transactionId,
                        REWARD_COINS,
                        true,
                        (success, updatedBalance, message) -> runOnUiThread(() -> {
                            isClaimFlowBusy = false;
                            if (binding == null) {
                                return;
                            }
                            setClaimButtonLoading(false);
                            if (success) {
                                playCoinCelebrateAnimation();
                                showSuccessMessage(getString(R.string.survey_rewards_success));
                            } else {
                                showErrorMessage(message);
                            }
                        })
                );
            }

            @Override
            public void onRewardNotEarned() {
                runOnUiThread(() -> {
                    isClaimFlowBusy = false;
                    if (binding == null) {
                        return;
                    }
                    setClaimButtonLoading(false);
                    showErrorMessage(getString(R.string.survey_rewards_ad_not_completed));
                });
            }

            @Override
            public void onAdUnavailableOrFailed(@NonNull String reason) {
                runOnUiThread(() -> {
                    isClaimFlowBusy = false;
                    if (binding == null) {
                        return;
                    }
                    setClaimButtonLoading(false);
                    showErrorMessage(getString(R.string.survey_rewards_ad_unavailable));
                });
            }
        });
    }

    private void setClaimButtonLoading(boolean loading) {
        binding.btnClaimSurveyReward.setEnabled(!loading);
        binding.btnClaimSurveyReward.setClickable(!loading);
        binding.btnClaimSurveyReward.setAlpha(loading ? 0.72f : 1.0f);
        binding.claimSurveyRewardProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnClaimSurveyRewardText.setText(
                loading ? R.string.survey_rewards_loading_ad : R.string.survey_rewards_claim_button
        );
        binding.btnClaimSurveyRewardGlow.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    private void showSuccessMessage(@NonNull String message) {
        binding.tvSurveyRewardStatus.setBackgroundResource(R.drawable.bg_survey_option_correct);
        binding.tvSurveyRewardStatus.setTextColor(ContextCompat.getColor(this, R.color.emerald_bright));
        binding.tvSurveyRewardStatus.setText(message);
        binding.tvSurveyRewardStatus.setVisibility(View.VISIBLE);
    }

    private void showErrorMessage(@NonNull String message) {
        binding.tvSurveyRewardStatus.setBackgroundResource(R.drawable.bg_survey_option_wrong);
        binding.tvSurveyRewardStatus.setTextColor(ContextCompat.getColor(this, R.color.auth_error));
        binding.tvSurveyRewardStatus.setText(message);
        binding.tvSurveyRewardStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatusMessage() {
        binding.tvSurveyRewardStatus.setVisibility(View.GONE);
    }

    private void animateEntrance() {
        binding.cardSurveyReward.setAlpha(0f);
        binding.cardSurveyReward.setTranslationY(dpToPx(18));
        binding.cardSurveyReward.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startAmbientAnimations() {
        if (binding == null || ambientAnimatorSet != null) {
            return;
        }
        ObjectAnimator coinScaleX = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoin, "scaleX", 1.0f, 1.07f);
        coinScaleX.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator coinScaleY = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoin, "scaleY", 1.0f, 1.07f);
        coinScaleY.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator haloAlpha = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoinGlow, "alpha", 0.48f, 0.96f);
        haloAlpha.setRepeatCount(ValueAnimator.INFINITE);
        haloAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator ctaGlowAlpha = ObjectAnimator.ofFloat(binding.btnClaimSurveyRewardGlow, "alpha", 0.45f, 0.92f);
        ctaGlowAlpha.setRepeatCount(ValueAnimator.INFINITE);
        ctaGlowAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ambientAnimatorSet = new AnimatorSet();
        ambientAnimatorSet.playTogether(coinScaleX, coinScaleY, haloAlpha, ctaGlowAlpha);
        ambientAnimatorSet.setDuration(1400L);
        ambientAnimatorSet.start();
    }

    private void playCoinCelebrateAnimation() {
        if (binding == null) {
            return;
        }
        binding.ivSurveyRewardCoin.animate()
                .rotationYBy(360f)
                .setDuration(600L)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ambientAnimatorSet != null && ambientAnimatorSet.isPaused()) {
            ambientAnimatorSet.resume();
        }
    }

    @Override
    protected void onPause() {
        if (ambientAnimatorSet != null && ambientAnimatorSet.isRunning()) {
            ambientAnimatorSet.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ambientAnimatorSet != null) {
            ambientAnimatorSet.cancel();
            ambientAnimatorSet = null;
        }
        if (adManager != null) {
            adManager.destroy();
        }
        binding = null;
        super.onDestroy();
    }
}
