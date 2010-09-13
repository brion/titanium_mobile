/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiUIClipboardProxy.h"
#import "TiUtils.h"
#import "TiApp.h"
#import "TiBlob.h"
#import "TiFile.h"
#import "TiUtils.h"

#import <MobileCoreServices/UTType.h>
#import <MobileCoreServices/UTCoreTypes.h>

typedef enum {
	CLIPBOARD_TEXT,
	CLIPBOARD_URI_LIST,
	CLIPBOARD_IMAGE,
	CLIPBOARD_UNKNOWN
} ClipboardType;

static ClipboardType mimeTypeToDataType(NSString *mimeType)
{
	mimeType = [mimeType lowercaseString];
    
	// Types "URL" and "Text" are for IE compatibility. We want to have
	// a consistent interface with WebKit's HTML 5 DataTransfer.
	if ([mimeType isEqualToString: @"text"] || [mimeType hasPrefix: @"text/plain"])
         {
             return CLIPBOARD_TEXT;
         }
         else if ([mimeType isEqualToString: @"url"] || [mimeType hasPrefix: @"text/uri-list"])
         {
             return CLIPBOARD_URI_LIST;
         }
         else if ([mimeType hasPrefix: @"image"])
         {
             return CLIPBOARD_IMAGE;
         }
         else
         {
             // Something else, work from the MIME type.
             return CLIPBOARD_UNKNOWN;
         }
}

static NSString *mimeTypeToUTType(NSString *mimeType)
{
    NSString *uti = (NSString *)UTTypeCreatePreferredIdentifierForTag(kUTTagClassMIMEType, (CFStringRef)mimeType, NULL);
    if (uti == nil) {
        // Should we do this? Lets us copy/paste custom data, anyway.
        uti = mimeType;
    }
    return uti;
}
         
         @implementation TiUIClipboardProxy
                  
         -(void)clearData:(id)args
    {
        ENSURE_UI_THREAD(clearData,args);
        ENSURE_STRING_OR_NIL(args);
        NSString *mimeType = args;
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        ClipboardType dataType = mimeTypeToDataType(mimeType);
        switch (dataType)
        {
            case CLIPBOARD_TEXT:
            {
                board.strings = nil;
                break;
            }
            case CLIPBOARD_URI_LIST:
            {
                board.URLs = nil;
                break;
            }
            case CLIPBOARD_IMAGE:
            {
                board.images = nil;
                break;
            }
            case CLIPBOARD_UNKNOWN:
            default:
            {
                [board setData: nil forPasteboardType: mimeTypeToUTType(mimeType)];
            }
        }
    }
         
         -(void)clearText:(id)args
    {
        ENSURE_UI_THREAD(clearText,args);
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        board.strings = nil;
    }
         
         -(id)getData:(id)args
    {
        // @fixme no worky here i think
        ENSURE_UI_THREAD(getData,args);
        ENSURE_STRING_OR_NIL(args);
        
        NSString *mimeType = args;
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        ClipboardType dataType = mimeTypeToDataType(mimeType);
        
        switch (dataType)
        {
            case CLIPBOARD_TEXT:
            {
                return board.string;
            }
            case CLIPBOARD_URI_LIST:
            {
                return board.URL;
            }
            case CLIPBOARD_IMAGE:
            {
                UIImage *image = board.image;
                if (image) {
                    return [[TiBlob alloc] initWithImage: image];
                } else {
                    return nil;
                }
            }
            case CLIPBOARD_UNKNOWN:
            default:
            {
                NSData *data = [board dataForPasteboardType: mimeTypeToUTType(mimeType)];
                if (data) {
                    return [[TiBlob alloc] initWithData: data mimetype: mimeType];
                } else {
                    return nil;
                }
            }
        }
    }
         
         -(NSString *)getText:(id)args
    {
        // @fixme
        ENSURE_UI_THREAD(getText,args);
        
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        return board.string;
    }
         
         -(BOOL)hasData:(id)args
    {
        // @fixme
        ENSURE_UI_THREAD(hasData,args);
        
        NSString *mimeType = [args objectAtIndex: 0];
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        ClipboardType dataType = mimeTypeToDataType(mimeType);
        
        switch (dataType)
        {
            case CLIPBOARD_TEXT:
            {
                return [board containsPasteboardTypes: UIPasteboardTypeListString];
            }
            case CLIPBOARD_URI_LIST:
            {
                return [board containsPasteboardTypes: UIPasteboardTypeListURL];
            }
            case CLIPBOARD_IMAGE:
            {
                return [board containsPasteboardTypes: UIPasteboardTypeListImage];
            }
            case CLIPBOARD_UNKNOWN:
            default:
            {
                return [board containsPasteboardTypes: [NSArray arrayWithObject: mimeTypeToUTType(mimeType)]];
            }
        }
    }
         
         -(BOOL)hasText:(id)args
    {
        // @fixme
        ENSURE_UI_THREAD(hasText,args);
        
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        return [board containsPasteboardTypes: UIPasteboardTypeListString];
    }
         
         -(void)setData:(id)args
    {
        ENSURE_UI_THREAD(setData,args);
        
        NSString *mimeType = [TiUtils stringValue: [args objectAtIndex: 0]];
        id data = [args objectAtIndex: 1];
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        ClipboardType dataType = mimeTypeToDataType(mimeType);
        
        switch (dataType)
        {
            case CLIPBOARD_TEXT:
            {
                board.string = [TiUtils stringValue: data];
                break;
            }
            case CLIPBOARD_URI_LIST:
            {
                board.URL = [NSURL URLWithString: [TiUtils stringValue: data]];
                break;
            }
            case CLIPBOARD_IMAGE:
            {
                board.image = [TiUtils toImage: data proxy: self];
                break;
            }
            case CLIPBOARD_UNKNOWN:
            default:
            {
                NSData *raw;
                if ([data isKindOfClass:[TiBlob class]])
                {
                    raw = [(TiBlob *)data data];
                }
                else if ([data isKindOfClass:[TiFile class]])
                {
                    raw = [[(TiFile *)data blob] data];
                }
                else
                {
                    raw = [[TiUtils stringValue: data] dataUsingEncoding: NSUTF8StringEncoding];
                }
                
                [board setData: raw forPasteboardType: mimeTypeToUTType(mimeType)];
            }
        }
    }
         
         -(void)setText:(id)args
    {
        ENSURE_UI_THREAD(setText,args);
        ENSURE_STRING_OR_NIL(args);
        NSString *text = args;
        
        UIPasteboard *board = [UIPasteboard generalPasteboard];
        // This will clear any other data. Is that right?
        board.string = text;
    }
         
         @end
