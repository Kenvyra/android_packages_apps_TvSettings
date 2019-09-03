/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.twopanelsettings.slices;

import static android.app.slice.Slice.HINT_PARTIAL;
import static android.app.slice.Slice.HINT_SUMMARY;
import static android.app.slice.Slice.HINT_TITLE;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_LONG;
import static android.app.slice.SliceItem.FORMAT_SLICE;
import static android.app.slice.SliceItem.FORMAT_TEXT;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.core.graphics.drawable.IconCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.slice.SliceItem;
import androidx.slice.core.SliceActionImpl;
import androidx.slice.core.SliceQuery;
import androidx.slice.widget.SliceContent;

import com.android.tv.twopanelsettings.IconUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Generate corresponding preference based upon the slice data.
 */
public final class SlicePreferencesUtil {

    static Preference getPreference(SliceItem item, ContextThemeWrapper contextThemeWrapper,
            String className) {
        Preference preference = null;
        Data data = extract(item);
        if (item.getSubType() != null) {
            if (item.getSubType().equals(SlicesConstants.TYPE_PREFERENCE)) {
                // TODO: Figure out all the possible cases and reorganize the logic
                if (data.mIntentItem != null) {
                    SliceActionImpl action = new SliceActionImpl(data.mIntentItem);
                    if (action != null) {
                        // Currently if we don't set icon for the SliceAction, slice lib will
                        // automatically treat it as a toggle. To distinguish preference action and
                        // toggle action, we need to add a subtype if this is a preference action.
                        preference = new SlicePreference(contextThemeWrapper);
                        ((SlicePreference) preference).setSliceAction(action);
                        if (data.mFollowupIntentItem != null) {
                            SliceActionImpl followUpAction =
                                    new SliceActionImpl(data.mFollowupIntentItem);
                            ((SlicePreference) preference).setFollowupSliceAction(followUpAction);
                        }
                    }
                } else if (data.mEndItems.size() > 0 && data.mEndItems.get(0) != null) {
                    SliceActionImpl action = new SliceActionImpl(data.mEndItems.get(0));
                    if (action != null) {
                        boolean isCheckMark = SlicePreferencesUtil.isCheckMark(item);
                        if (isCheckMark) {
                            preference = new SliceCheckboxPreference(contextThemeWrapper, action);
                        } else {
                            preference = new SliceSwitchPreference(contextThemeWrapper, action);
                        }
                    }
                }

                CharSequence uri = getText(data.mTargetSliceItem);
                if (uri == null || TextUtils.isEmpty(uri)) {
                    if (preference == null) {
                        preference = new Preference(contextThemeWrapper);
                    }
                } else {
                    if (preference == null) {
                        preference = new SlicePreference(contextThemeWrapper);
                    }
                    ((SlicePreference) preference).setUri(uri.toString());
                    preference.setFragment(className);
                }
            } else if (item.getSubType().equals(SlicesConstants.TYPE_PREFERENCE_CATEGORY)) {
                preference = new PreferenceCategory(contextThemeWrapper);
            }
        }

        if (preference != null) {
            // Set the key for the preference
            CharSequence key = getKey(item);
            if (key != null) {
                preference.setKey(key.toString());
            }

            Icon icon = getIcon(data.mStartItem);
            if (icon != null) {
                boolean isIconNeedToBeProcessed =
                        SlicePreferencesUtil.isIconNeedsToBeProcessed(item);
                Drawable iconDrawable = icon.loadDrawable(contextThemeWrapper);
                if (isIconNeedToBeProcessed) {
                    preference.setIcon(IconUtil.getCompoundIcon(contextThemeWrapper, iconDrawable));
                } else {
                    preference.setIcon(iconDrawable);
                }
            }

            if (data.mTitleItem != null) {
                preference.setTitle(getText(data.mTitleItem));
            }

            //Set summary
            CharSequence subtitle =
                    data.mSubtitleItem != null ? data.mSubtitleItem.getText() : null;
            boolean subtitleExists = !TextUtils.isEmpty(subtitle)
                    || (data.mSubtitleItem != null && data.mSubtitleItem.hasHint(HINT_PARTIAL));
            if (subtitleExists) {
                preference.setSummary(subtitle);
            } else {
                if (data.mSummaryItem != null) {
                    preference.setSummary(getText(data.mSummaryItem));
                }
            }

        }

        return preference;
    }

    static class Data {
        SliceItem mStartItem;
        SliceItem mTitleItem;
        SliceItem mSubtitleItem;
        SliceItem mSummaryItem;
        SliceItem mTargetSliceItem;
        SliceItem mIntentItem;
        SliceItem mFollowupIntentItem;
        List<SliceItem> mEndItems = new ArrayList<>();
    }

    static Data extract(SliceItem sliceItem) {
        Data data = new Data();
        List<SliceItem> possibleStartItems =
                SliceQuery.findAll(sliceItem, null, HINT_TITLE, null);
        if (possibleStartItems.size() > 0) {
            // The start item will be at position 0 if it exists
            String format = possibleStartItems.get(0).getFormat();
            if ((FORMAT_ACTION.equals(format)
                    && SliceQuery.find(possibleStartItems.get(0), FORMAT_IMAGE) != null)
                    || FORMAT_SLICE.equals(format)
                    || FORMAT_LONG.equals(format)
                    || FORMAT_IMAGE.equals(format)) {
                data.mStartItem = possibleStartItems.get(0);
            }
        }

        List<SliceItem> items = sliceItem.getSlice().getItems();
        for (int i = 0; i < items.size(); i++) {
            final SliceItem item = items.get(i);
            String subType = item.getSubType();
            if (subType != null) {
                switch (subType) {
                    case SlicesConstants.SUBTYPE_INTENT :
                        data.mIntentItem = item;
                        break;
                    case SlicesConstants.SUBTYPE_FOLLOWUP_INTENT :
                        data.mFollowupIntentItem = item;
                        break;
                    case SlicesConstants.TAG_TARGET_URI :
                        data.mTargetSliceItem = item;
                        break;
                }
            } else if (FORMAT_TEXT.equals(item.getFormat()) && (item.getSubType() == null)) {
                if ((data.mTitleItem == null || !data.mTitleItem.hasHint(HINT_TITLE))
                        && item.hasHint(HINT_TITLE) && !item.hasHint(HINT_SUMMARY)) {
                    data.mTitleItem = item;
                } else if (data.mSubtitleItem == null && !item.hasHint(HINT_SUMMARY)) {
                    data.mSubtitleItem = item;
                } else if (data.mSummaryItem == null && item.hasHint(HINT_SUMMARY)) {
                    data.mSummaryItem = item;
                }
            } else {
                data.mEndItems.add(item);
            }
        }
        data.mEndItems.remove(data.mStartItem);
        return data;
    }

    private static CharSequence getKey(SliceItem item) {
        SliceItem target = SliceQuery.findSubtype(item, FORMAT_TEXT, SlicesConstants.TAG_KEY);
        if (target != null) {
            return target.getText();
        } else {
            return null;
        }
    }

    /**
     * Get the screen title item for the slice.
     * @param sliceItems list of SliceItem extracted from slice data.
     * @return screen title item.
     */
    static SliceItem getScreenTitleItem(List<SliceContent> sliceItems) {
        for (SliceContent contentItem : sliceItems)  {
            SliceItem item = contentItem.getSliceItem();
            if (item.getSubType() != null
                    && item.getSubType().equals(SlicesConstants.TYPE_PREFERENCE_SCREEN_TITLE)) {
                return item;
            }
        }
        return null;
    }

    private static boolean isIconNeedsToBeProcessed(SliceItem sliceItem) {
        List<SliceItem> items = sliceItem.getSlice().getItems();
        for (SliceItem item : items)  {
            if (item.getSubType() != null && item.getSubType().equals(
                    SlicesConstants.SUBTYPE_ICON_NEED_TO_BE_PROCESSED)) {
                return item.getInt() == 1;
            }
        }
        return false;
    }

    private static boolean isCheckMark(SliceItem sliceItem) {
        List<SliceItem> items = sliceItem.getSlice().getItems();
        for (SliceItem item : items)  {
            if (item.getSubType() != null
                    && item.getSubType().equals(SlicesConstants.SUBTYPE_IS_CHECK_MARK)) {
                return item.getInt() == 1;
            }
        }
        return false;
    }

    /**
     * Get the text from the SliceItem.
     */
    static CharSequence getText(SliceItem item) {
        if (item == null) {
            return null;
        }
        return item.getText();
    }

    /** Get the icon from the SlicItem if available */
    static Icon getIcon(SliceItem startItem) {
        if (startItem != null && startItem.getSlice() != null
                && startItem.getSlice().getItems() != null
                && startItem.getSlice().getItems().size() > 0) {
            SliceItem iconItem = startItem.getSlice().getItems().get(0);
            if (FORMAT_IMAGE.equals(iconItem.getFormat())) {
                IconCompat icon = iconItem.getIcon();
                return icon.toIcon();
            }
        }
        return null;
    }

    static Uri getStatusPath(String uriString) {
        Uri statusUri = Uri.parse(uriString)
                .buildUpon().path("/" + SlicesConstants.PATH_STATUS).build();
        return statusUri;
    }
}