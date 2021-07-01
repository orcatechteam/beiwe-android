package org.orcatech.mobileresearchtool.survey;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
//import android.widget.TextView;
import androidx.appcompat.widget.AppCompatTextView;

import com.commonsware.cwac.anddown.AndDown;

/**
 * Created by elijones on 1/20/17.
 */

public class MarkDownTextView extends AppCompatTextView {
	// Duplicating all Constructors for maximum trivial compatibility.
	public MarkDownTextView(Context context) { super(context); }
	public MarkDownTextView(Context context, AttributeSet attrs) { super(context, attrs); }
	public MarkDownTextView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
//	public MarkDownTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

	/** Takes a markdown formatted String and applies the formatting to the TextView object.
	 * @param markDownText markdown formatted text string */
	public void setMarkDownText(String markDownText) {
		AndDown markDownConverter = new AndDown();
		String markDownHtml = markDownConverter.markdownToHtml(markDownText);
		super.setText( Html.fromHtml(markDownHtml) );
		super.setMovementMethod(LinkMovementMethod.getInstance());
	}

	/** This should make usage trivial, just use the settext function on
	 * your MarkDownTextView objects and pass them Strings.
	 * @param markDownText markdown formatted text string */
	public void setText(String markDownText){ setMarkDownText(markDownText); }
}
