/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiUtils.h"
#import "TiUISearchBarProxy.h"
#import "TiUISearchBar.h"

@implementation TiUISearchBar

-(void)dealloc
{
	[searchView setDelegate:nil];
	RELEASE_TO_NIL(searchView);
	[super dealloc];
}

-(void)layoutSubviews
{
	[super layoutSubviews];
	if (searchView==nil)
	{
		searchView = [[UISearchBar alloc] init];
		[searchView setAutoresizingMask:UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight];
		[TiUtils setView:searchView positionRect:[self bounds]];
		[(TiViewProxy *)[self proxy] firePropertyChanges];
		[searchView setDelegate:(TiUISearchBarProxy*)[self proxy]];
		[self addSubview:searchView];
	}
}

#pragma mark View controller stuff

-(void)blur:(id)args
{
	ENSURE_UI_THREAD(blur,nil);
	[searchView resignFirstResponder];
}

-(void)focus:(id)args
{
	ENSURE_UI_THREAD(focus,nil);
	[searchView becomeFirstResponder];
}


-(void)setValue_:(id)value
{
	[searchView setText:[TiUtils stringValue:value]];
}

-(void)setShowCancel_:(id)value
{
	[searchView setShowsCancelButton:[TiUtils boolValue:value]];
}

-(void)setHintText_:(id)value
{
	[searchView setPlaceholder:[TiUtils stringValue:value]];
}

-(void)setKeyboardType_:(id)value
{
	[searchView setKeyboardType:[TiUtils intValue:value]];
}

-(void)setAutocorrect_:(id)value
{
	[searchView setAutocorrectionType:[TiUtils boolValue:value] ? UITextAutocorrectionTypeYes : UITextAutocorrectionTypeNo];
}

-(void)setAutocapitalization_:(id)value
{
	[searchView setAutocapitalizationType:[TiUtils intValue:value]];
}


@property(nonatomic) UITextAutocapitalizationType autocapitalizationType;  // default is UITextAutocapitalizationTypeNone
@property(nonatomic) UITextAutocorrectionType     autocorrectionType;      // default is UITextAutocorrectionTypeDefault
@property(nonatomic) UIKeyboardType               keyboardType;            // default is UIKeyboardTypeDefault


-(void)setBarColor_:(id)value
{
	TiColor * newBarColor = [TiUtils colorValue:value];
	[searchView setBarStyle:[TiUtils barStyleForColor:newBarColor]];
	[searchView setTintColor:[TiUtils barColorForColor:newBarColor]];
	[searchView setTranslucent:[TiUtils barTranslucencyForColor:newBarColor]];
}


@end
