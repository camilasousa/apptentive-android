/*
 * Copyright (c) 2012, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.*;

import com.apptentive.android.sdk.comm.ApptentiveClient;
import com.apptentive.android.sdk.module.survey.*;
import com.apptentive.android.sdk.offline.PayloadManager;
import com.apptentive.android.sdk.offline.SurveyPayload;
import com.apptentive.android.sdk.util.Util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This module is responsible for fetching, displaying, and sending finished survey payloads to the apptentive server.
 *
 * @author Sky Kelsey
 */
public class SurveyModule {

	// *************************************************************************************************
	// ********************************************* Static ********************************************
	// *************************************************************************************************

	private static SurveyModule instance;

	static SurveyModule getInstance() {
		if (instance == null) {
			instance = new SurveyModule();
		}
		return instance;
	}


	// *************************************************************************************************
	// ********************************************* Private *******************************************
	// *************************************************************************************************

	private SurveyDefinition surveyDefinition;
	private SurveyPayload result;
	private SurveySendView sendView;
	private boolean fetching = false;

	private SurveyModule() {
		surveyDefinition = null;
	}

	private void setSurvey(SurveyDefinition surveyDefinition) {
		this.surveyDefinition = surveyDefinition;
	}


	// *************************************************************************************************
	// ******************************************* Not Private *****************************************
	// *************************************************************************************************

	/**
	 * Fetches a survey.
	 *
	 * @param onSurveyFetchedListener An optional {@link OnSurveyFetchedListener} that will be notified when the
	 *                                survey has been fetched. Pass in null if you don't need to be notified.
	 */
	public synchronized void fetchSurvey(final OnSurveyFetchedListener onSurveyFetchedListener) {
		if (fetching) {
			Log.d("Already fetching survey");
			return;
		}
		Log.d("Started survey fetch");
		fetching = true;
		// Upload any payloads that were created while the device was offline.
		new Thread() {
			public void run() {
				try {
					ApptentiveClient client = new ApptentiveClient(GlobalInfo.apiKey);
					SurveyDefinition definition = client.getSurvey();
					if (definition != null) {
						setSurvey(definition);
					}
					if (onSurveyFetchedListener != null) {
						onSurveyFetchedListener.onSurveyFetched(definition != null);
					}
				} finally {
					fetching = false;
				}
			}
		}.start();
	}

	public void show(Context context) {
		if (!isSurveyReady()) {
			return;
		}
		Intent intent = new Intent();
		intent.setClass(context, ApptentiveActivity.class);
		intent.putExtra("module", ApptentiveActivity.Module.SURVEY.toString());
		context.startActivity(intent);
	}

	public boolean isSurveyReady() {
		return (surveyDefinition != null);
	}

	public void cleanup() {
		this.surveyDefinition = null;
		this.result = null;
	}

	boolean isSkippable() {
		return !surveyDefinition.isRequired();
	}

	boolean isCompleted() {
		for (int i = 0; i < surveyDefinition.getQuestions().size(); i++) {
			Question question = surveyDefinition.getQuestions().get(i);
			if (question.isRequired() && !result.isAnswered(question.getId())) {
				return false;
			}
		}
		return true;
	}

	void setAnswer(int questionIndex, String... answer) {
		result.setAnswer(questionIndex, answer);
		sendView.setEnabled(isCompleted());
	}

	void doShow(final Activity activity) {
		if (surveyDefinition == null) {
			return;
		}
		result = new SurveyPayload(surveyDefinition);

		TextView surveyTitle = (TextView) activity.findViewById(R.id.apptentive_survey_title_text);
		surveyTitle.setFocusable(true);
		surveyTitle.setFocusableInTouchMode(true);
		surveyTitle.setText(surveyDefinition.getName());

		Button skipButton = (Button) activity.findViewById(R.id.apptentive_survey_button_skip);
		if (surveyDefinition.isRequired()) {
			((RelativeLayout) skipButton.getParent()).removeView(skipButton);
		} else {
			skipButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					cleanup();
					activity.finish();
				}
			});
		}

		View brandingButton = activity.findViewById(R.id.apptentive_branding_view);
		brandingButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				AboutModule.getInstance().show(activity);
			}
		});

		LinearLayout questionList = (LinearLayout) activity.findViewById(R.id.aptentive_survey_question_list);

		// Render the survey description
		if (surveyDefinition.getDescription() != null) {
			SurveyDescriptionView surveyDescription = new SurveyDescriptionView(activity);
			surveyDescription.setTitleText(surveyDefinition.getDescription());
			questionList.addView(surveyDescription);
		}

		// Then render all the questions
		for (Question question : surveyDefinition.getQuestions()) {
			final int index = surveyDefinition.getQuestions().indexOf(question);
			if (question.getType() == Question.QUESTION_TYPE_SINGLELINE) {
				SinglelineQuestion temp = (SinglelineQuestion) question;
				TextSurveyQuestionView textQuestionView = new TextSurveyQuestionView(activity);
				textQuestionView.setTitleText(temp.getValue());
				textQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener<TextSurveyQuestionView>() {
					public void onAnswered(TextSurveyQuestionView view) {
						setAnswer(index, view.getAnswer());
					}
				});
				questionList.addView(textQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTICHOICE) {
				MultichoiceQuestion temp = (MultichoiceQuestion) question;
				MultichoiceSurveyQuestionView multichoiceQuestionView = new MultichoiceSurveyQuestionView(activity);
				multichoiceQuestionView.setTitleText(temp.getValue());
				multichoiceQuestionView.setAnswers(temp.getAnswerChoices());
				multichoiceQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener<MultichoiceSurveyQuestionView>() {
					public void onAnswered(MultichoiceSurveyQuestionView view) {
						Map<String, Boolean> answers = view.getAnswers();
						for (String id : answers.keySet()) {
							boolean answered = answers.get(id);
							if (answered) {
								setAnswer(index, id);
								break;
							}
						}
					}
				});
				questionList.addView(multichoiceQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTISELECT) {
				MultiselectQuestion temp = (MultiselectQuestion) question;
				MultiselectSurveyQuestionView multiselectQuestionView = new MultiselectSurveyQuestionView(activity);
				multiselectQuestionView.setTitleText(temp.getValue());
				multiselectQuestionView.setAnswers(temp.getAnswerChoices());
				multiselectQuestionView.setMaxChoices(temp.getMaxSelections());
				multiselectQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener<MultiselectSurveyQuestionView>() {
					public void onAnswered(MultiselectSurveyQuestionView view) {
						Map<String, Boolean> answers = view.getAnswers();
						Set<String> answersSet = new HashSet<String>();
						for (String id : answers.keySet()) {
							boolean answered = answers.get(id);
							if (answered) {
								answersSet.add(id);
							}
						}
						if(answersSet.isEmpty()) {
							setAnswer(index, "");
						} else {
							setAnswer(index, (String[]) answersSet.toArray(new String[]{}));
						}
					}
				});
				questionList.addView(multiselectQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_STACKRANK) {
				// TODO: This.
			}
		}

		// Then render the send button.
		sendView = new SurveySendView(activity);
		sendView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Util.hideSoftKeyboard(activity, view);
				PayloadManager.getInstance().putPayload(result);
				if (surveyDefinition.isShowSuccessMessage() && surveyDefinition.getSuccessMessage() != null) {
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setMessage(surveyDefinition.getSuccessMessage());
					builder.setTitle("Survey Completed");
					builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							cleanup();
							activity.finish();
						}
					});
					builder.show();
				} else {
					cleanup();
					activity.finish();
				}
			}
		});
		sendView.setEnabled(isCompleted());
		questionList.addView(sendView);

		// Force the top of the survey to be shown first.
		surveyTitle.requestFocus();
	}
}
