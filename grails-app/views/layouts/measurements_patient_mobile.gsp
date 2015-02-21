<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style type="text/css">
    body {
        background-color:transparent;
    }
    
    h1 {
        color: #b81845;
        text-shadow: 3px 3px 3px #B3B3B3;
        text-decoration: none;
        font-weight: 100;
        font-size: 2.3em;
        margin: 0;
        padding-left: 0.6em
    }

    h1.information {
        color: black;
        text-align: center;
    }

    hr {
        display: block;
        height: 1px;
        border: 0;
        border-top: 1px solid #000000;
        margin: 1em 0;
        padding: 0;
    }

    a:link, a:visited, a:hover, a:active {
        color: #b81845;
        text-shadow: 3px 3px 3px #B3B3B3;
        text-decoration: none;
    }

    .period_adjustment {
        width: 100%;
        text-align: center;
        padding: 1em;
        font-size: 1.2em;
    }

    .measurements table {
        text-align: center;
        border-collapse: collapse;
        margin-left: auto;
        margin-right: auto;
        width: 80%;
    }

    .measurements > table {
        margin-top: 2em;
    }

    .measurements th {
        border-width: 1px;
        font-size: 2em;
        color: #333333;
        border-style: solid;
        border-color: #000000;
        background-color: #ABA7AB;

    }

    .measurements > table > tbody > tr > td {
        border-width: 1px;
        padding: 1em;
        font-size: 1.3em;
        color: #777;
        border-style: solid;
        border-color: #000000;
    }


    </style>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'bloodsugartable.css')}" type="text/css"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery-ui.custom.css')}" type="text/css"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'font-awesome.min.css')}" type="text/css">
    <g:javascript src="jquery.js"/>
    <g:javascript src="jquery-ui/jquery-ui-1.10.1.min.js" />
</head>

<body>
<g:layoutBody/>
</body>
</html>