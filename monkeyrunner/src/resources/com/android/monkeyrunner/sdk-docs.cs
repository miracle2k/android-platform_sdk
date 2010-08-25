page.title=UI/Application Exerciser Monkey API
@jd:body

<h2>MonkeyRunner Help<h2>
<h3>Table of Contents</h3>
<ul>
<?cs each:item = help ?>
<li><a href="#<?cs name:item ?>"><?cs var:item.name ?></a></li>
<?cs /each ?>
</ul>
<?cs each:item = help ?>
<h3><a name="<?cs name:item ?>"><?cs var:item.name ?></a></h3>
  <?cs each:docpara = item.doc ?>
  <p><?cs var:docpara ?></p>
  <?cs /each ?>
    <?cs if:subcount(item.argument) ?>
<h4>Args</h4>
<ul>
      <?cs each:arg = item.argument ?>
        <li><?cs var:arg.name ?> - <?cs each:argdocpara = arg.doc ?><?cs var:argdocpara ?> <?cs /each ?>
      <?cs /each ?>
</ul>
<h4>Returns</h4>
<p><?cs each:retdocpara = item.returns ?><?cs var:retdocpara ?> <?cs /each ?></p>
<?cs /if ?>
<?cs /each ?>
</body>
</html>
