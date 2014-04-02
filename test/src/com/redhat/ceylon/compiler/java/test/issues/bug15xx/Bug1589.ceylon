{Element*} concat<Element>({Element*}* iterables)
        => { for (elements in iterables) for (element in elements) element };
[]|[Element]|[Element,Element] noneOneOrTwo<Element>(Element element)
        => [element];

[String, String, String] notempty = ["one", "two", "three"];
{String*} spreadNotempty = concat(*notempty.map(noneOneOrTwo<String>));
{String*} spreadWrappedNotempty = concat(*{*notempty.map(noneOneOrTwo<String>)});
{Nothing*} spreadEmpty = concat(*[].map(noneOneOrTwo<Nothing>));
{Nothing*} spreadWrappedEmpty = concat(*{*[].map(noneOneOrTwo<Nothing>)});

object myEmpty satisfies {Nothing*} {
    shared actual Iterator<Nothing> iterator() {
        object iterator satisfies Iterator<Nothing> {
            shared actual Finished next() => finished;
        }
        return iterator;
    }
    
    shared actual {Nothing*} map<Result>(Result collecting(Nothing elem))
            => this; // don't try to cast this to Sequential!
}

{Nothing*} spreadMyEmpty = concat(*myEmpty.map(noneOneOrTwo<Nothing>));
{Nothing*} spreadWrappedMyEmpty = concat(*{*myEmpty.map(noneOneOrTwo<Nothing>)});

{Nothing*} wrap4 = concat(*{*{*[*{
    myEmpty.map(noneOneOrTwo<Nothing>)}]}});


{Element+} concatPlus<Element>({Element+}+ iterables)
        => { for (elements in iterables) for (element in elements) element };

{String+} spreadNotemptyPlus = concatPlus(*{notempty});
{String+} spreadWrappedNotemptyPlus = concatPlus(*{*{notempty}});

object myNotempty satisfies {String+} {
    shared actual Iterator<String> iterator() => { "one", "two", "three" }.iterator();
}

{String+} spreadMyNotemptyPlus = concatPlus(*{myNotempty});
{String+} spreadWrappedMyNotemptyPlus = concatPlus(*{*{myNotempty}});

void run1589() {
    // sanity check
    assert (myNotempty.sequence == notempty.sequence);
    assert (myEmpty.sequence == empty.sequence);
    // tests
    assert (spreadNotempty.sequence == notempty.sequence);
    assert (spreadWrappedNotempty.sequence == notempty.sequence);
    assert (spreadEmpty.sequence == empty.sequence);
    assert (spreadWrappedEmpty.sequence == empty.sequence);
    assert (spreadMyEmpty.sequence == myEmpty.sequence);
    assert (spreadWrappedMyEmpty.sequence == myEmpty.sequence);
    assert (wrap4.sequence == myEmpty.sequence);
    assert (spreadNotemptyPlus.sequence == notempty.sequence);
    assert (spreadWrappedNotemptyPlus.sequence == myNotempty.sequence);
    assert (spreadMyNotemptyPlus.sequence == notempty.sequence);
    assert (spreadWrappedMyNotemptyPlus.sequence == myNotempty.sequence);
}
